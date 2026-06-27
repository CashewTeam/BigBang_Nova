cmake_minimum_required(VERSION 3.22.1)

project(textboom_jieba)

add_library(textboom_jieba SHARED jieba_jni.cpp)

target_compile_features(textboom_jieba PRIVATE cxx_std_17)

target_include_directories(textboom_jieba PRIVATE include)

find_library(log-lib log)

target_link_libraries(textboom_jieba ${log-lib})
