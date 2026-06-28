#include <jni.h>
#include <android/log.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "cppjieba/DictTrie.hpp"
#include "cppjieba/HMMModel.hpp"
#include "cppjieba/MixSegment.hpp"

namespace {

const char* kTag = "textboom_jieba";

std::mutex gMutex;
std::unique_ptr<cppjieba::DictTrie> gDictTrie;
std::unique_ptr<cppjieba::HMMModel> gHmmModel;
std::unique_ptr<cppjieba::MixSegment> gMixSegment;

std::string JStringToUtf8(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars == nullptr ? "" : chars);
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

std::vector<jint> BuildUtf16Offsets(JNIEnv* env, jstring text) {
    const jsize length = env->GetStringLength(text);
    const jchar* chars = env->GetStringChars(text, nullptr);
    std::vector<jint> offsets;
    offsets.reserve(static_cast<size_t>(length) + 1);
    for (jsize index = 0; index < length;) {
        offsets.push_back(index);
        const jchar current = chars[index];
        if (current >= 0xD800 && current <= 0xDBFF
                && index + 1 < length
                && chars[index + 1] >= 0xDC00
                && chars[index + 1] <= 0xDFFF) {
            index += 2;
        } else {
            index += 1;
        }
    }
    offsets.push_back(length);
    env->ReleaseStringChars(text, chars);
    return offsets;
}

std::string JoinPath(const std::string& dir, const char* fileName) {
    if (dir.empty()) {
        return fileName;
    }
    if (dir.back() == '/') {
        return dir + fileName;
    }
    return dir + "/" + fileName;
}

jboolean NativeInit(JNIEnv* env, jclass, jstring dictDir) {
    try {
        std::lock_guard<std::mutex> lock(gMutex);
        std::string dictRoot = JStringToUtf8(env, dictDir);
        gDictTrie.reset(new cppjieba::DictTrie(
                JoinPath(dictRoot, "jieba.dict.utf8"),
                JoinPath(dictRoot, "user.dict.utf8")));
        gHmmModel.reset(new cppjieba::HMMModel(
                JoinPath(dictRoot, "hmm_model.utf8")));
        gMixSegment.reset(new cppjieba::MixSegment(gDictTrie.get(), gHmmModel.get()));
        return JNI_TRUE;
    } catch (const std::exception& error) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "init failed: %s", error.what());
        gMixSegment.reset();
        gHmmModel.reset();
        gDictTrie.reset();
        return JNI_FALSE;
    }
}

jintArray NativeCut(JNIEnv* env, jclass, jstring text) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gMixSegment || text == nullptr) {
        return nullptr;
    }

    std::string utf8Text = JStringToUtf8(env, text);
    std::vector<jint> utf16Offsets = BuildUtf16Offsets(env, text);
    std::vector<cppjieba::Word> words;
    gMixSegment->Cut(utf8Text, words, true);

    std::vector<jint> spans;
    spans.reserve(words.size() * 2);
    for (size_t index = 0; index < words.size(); ++index) {
        const cppjieba::Word& word = words[index];
        const size_t codePointStart = word.unicode_offset;
        const size_t codePointEnd = codePointStart + word.unicode_length;
        if (word.unicode_length == 0 || codePointStart >= utf16Offsets.size() - 1) {
            continue;
        }
        const size_t safeEnd = codePointEnd >= utf16Offsets.size()
                ? utf16Offsets.size() - 1
                : codePointEnd;
        jint start = utf16Offsets[codePointStart];
        jint endExclusive = utf16Offsets[safeEnd];
        if (endExclusive <= start) {
            continue;
        }
        spans.push_back(start);
        spans.push_back(endExclusive - 1);
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(spans.size()));
    if (result != nullptr && !spans.empty()) {
        env->SetIntArrayRegion(result, 0, static_cast<jsize>(spans.size()), spans.data());
    }
    return result;
}

JNINativeMethod kMethods[] = {
        {"nativeInit", "(Ljava/lang/String;)Z", reinterpret_cast<void*>(NativeInit)},
        {"nativeCut", "(Ljava/lang/String;)[I", reinterpret_cast<void*>(NativeCut)},
};

} // namespace

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = env->FindClass("com/cashewteam/novatext/android/data/CppJiebaTokenizer");
    if (clazz == nullptr || env->RegisterNatives(clazz, kMethods,
            sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
