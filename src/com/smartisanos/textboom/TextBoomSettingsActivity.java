package com.smartisanos.textboom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.SectionIndexer;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.smartisanos.textboom.util.Constant;
import com.smartisanos.textboom.data.BigBangSettings;
import com.smartisanos.textboom.data.CppJiebaTokenizer;
import com.smartisanos.textboom.util.LogUtils;
import com.smartisanos.textboom.util.Utils;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import smartisanos.widget.SettingItemCheck;
import smartisanos.widget.SettingItemSwitch;
import smartisanos.widget.SettingItemText;
import smartisanos.widget.Title;
import smartisanos.api.IntentSmt;

public class TextBoomSettingsActivity extends Activity implements CompoundButton.OnCheckedChangeListener {

    private static final String TAG = TextBoomSettingsActivity.class.getSimpleName();

    private final static int CATEGORY_SEARCH = 1;
    private final static int CATEGORY_DICT = 2;

    private Toast mOcrSwitchToast;
    private BigBangSettings mSettings;

    private StickyListHeadersListView mOptionsList;
    private SettingItemSwitch mTextBoomSwitch;
    private SettingItemSwitch mOCRSwitch;
    private OptionsAdapter mAdapter;
    private SettingItemText mTextBoomTriggerAreaOption;
    private Spinner mDebugPresetSpinner;
    private EditText mDebugTextInput;
    private Button mDebugPreviewButton;
    private TextView mWarmUpStatusView;
    private final Handler mHandler = new Handler();
    private final Runnable mWarmUpStatusUpdater = new Runnable() {
        @Override
        public void run() {
            updateWarmUpStatus();
        }
    };
    private List<BigBangItem> mBigBangItemList = new ArrayList<BigBangItem>();
    private int mCurrentSearchValue;
    private int mCurrentDictValue;
    private BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String pkg = intent.getData().getSchemeSpecificPart();
            LogUtils.d(TAG, "action:" + action + " , pkg " + pkg);
            if (Constant.PKG_CAMSCANNER.equals(pkg)) {
                updateViews();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettings = BigBangSettings.get(this);
        setContentView(R.layout.sticky_options_list_layout);

        mOptionsList = (StickyListHeadersListView) findViewById(R.id.options_list);
        mOptionsList.setAreHeadersSticky(false);
        initData();
        addHeaderFooterView();
        mAdapter = new OptionsAdapter(this);
        mOptionsList.setAdapter(mAdapter);
        mOptionsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int index, long l) {
                int pos = index - mOptionsList.getHeaderViewsCount();
                LogUtils.d(TAG, "selected index " + pos);

                if (pos < 0 || l == -1)
                    return;
                BigBangItem selectedItem = mBigBangItemList.get(pos);
                LogUtils.d(TAG, "selectedItem  " + selectedItem.title + " , value: " + selectedItem.settingsValue);
                switch (selectedItem.category) {
                    case CATEGORY_SEARCH:
                        mCurrentSearchValue = selectedItem.settingsValue;
                        mSettings.setWebSearchType(selectedItem.settingsValue);
                        break;
                    case CATEGORY_DICT:
                        mCurrentDictValue = selectedItem.settingsValue;
                        mSettings.setDictSearchType(selectedItem.settingsValue);
                        break;

                    default:
                        break;
                }
                mAdapter.notifyDataSetChanged();
            }
        });
        Title title = (Title) findViewById(R.id.view_title);
        title.setTitle(R.string.text_boom_settings);
        title.getBackButton().setVisibility(View.GONE);
    }

    private void initData() {
        mBigBangItemList.clear();
        String[] displayTitles = getResources().getStringArray(R.array.text_boom_search_ways);
        int[] searchWayValues = getResources().getIntArray(R.array.text_boom_search_values);
        TypedArray searchIconArray = getResources().obtainTypedArray(R.array.text_boom_search_icons);
        for (int i = 0; i < displayTitles.length; i++) {
            BigBangItem item = new BigBangItem();
            item.category = CATEGORY_SEARCH;
            item.title = displayTitles[i];
            item.icon = searchIconArray.getDrawable(i);
            item.settingsValue = searchWayValues[i];
            mBigBangItemList.add(item);
        }
        searchIconArray.recycle();

        String[] dictTitles = getResources().getStringArray(R.array.big_bang_dict_name);
        int[] dictValues = getResources().getIntArray(R.array.big_bang_dict_value);
        TypedArray dictIconArray = getResources().obtainTypedArray(R.array.big_bang_dict_icon);
        for (int i = 0; i < dictTitles.length; i++) {
            BigBangItem item = new BigBangItem();
            item.category = CATEGORY_DICT;
            item.title = dictTitles[i];
            item.icon = dictIconArray.getDrawable(i);
            item.settingsValue = dictValues[i];
            mBigBangItemList.add(item);
        }
        dictIconArray.recycle();
    }

    private void addHeaderFooterView() {
        View headerView = getLayoutInflater().inflate(R.layout.text_boom_header_layout, null);
        mTextBoomSwitch = (SettingItemSwitch) headerView.findViewById(R.id.text_boom_switch);
        mOCRSwitch = (SettingItemSwitch) headerView.findViewById(R.id.ocr_switch);
        TextView bigBangTipsView = (TextView) headerView.findViewById(R.id.id_big_bang_tips);
        CharSequence text = getString(R.string.big_bang_tips);
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Pattern pattern = Pattern.compile("/tricorn/");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            builder.setSpan(new ImageSpanAlignCenter(this, R.drawable.bigbang_trio),
                    matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        pattern = Pattern.compile("/sogou/");
        matcher = pattern.matcher(text);

        while (matcher.find()) {
            builder.setSpan(new ImageSpanAlignCenter(this, R.drawable.bigbang_sogou),
                    matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        bigBangTipsView.setText(builder);


        TextView ocrTipsView = (TextView) headerView.findViewById(R.id.id_ocr_tips);
        text = getString(R.string.ocr_tips);
        builder = new SpannableStringBuilder(text);
        pattern = Pattern.compile("/camscanner/");
        matcher = pattern.matcher(text);

        while (matcher.find()) {
            builder.setSpan(new ImageSpanAlignCenter(this, R.drawable.bigbang_scan),
                    matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        ocrTipsView.setText(builder);
        mTextBoomTriggerAreaOption = (SettingItemText) headerView.findViewById(R.id.textboom_trigger_area_options);
        mTextBoomTriggerAreaOption.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                launchTextBoomTriggerAreaOptions();
            }
        });
        mDebugPresetSpinner = (Spinner) headerView.findViewById(R.id.debug_preset_spinner);
        mDebugTextInput = (EditText) headerView.findViewById(R.id.debug_preview_text);
        mDebugPreviewButton = (Button) headerView.findViewById(R.id.debug_preview_button);
        mWarmUpStatusView = (TextView) headerView.findViewById(R.id.debug_warm_up_status);
        mOptionsList.addHeaderView(headerView);
        mOptionsList.addFooterView(Utils.inflateListTransparentHeader(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCurrentSearchValue = mSettings.getWebSearchType();
        mCurrentDictValue = mSettings.getDictSearchType();
        updateViews();
        startWarmUpStatusUpdates();
        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addDataScheme("package");
        registerReceiver(mPackageReceiver, pkgFilter);
    }

    private void updateViews() {
        boolean isBigBangEnabled = mSettings.isBigBangEnabled();
        boolean isOCREnabled = false;
        mTextBoomSwitch.setChecked(isBigBangEnabled);
        mOCRSwitch.setChecked(isOCREnabled);
        mOCRSwitch.setEnabled(false);
        mSettings.setOcrEnabled(false);
        mTextBoomSwitch.setOnCheckedChangeListener(this);
        mOCRSwitch.setOnCheckedChangeListener(this);
        mTextBoomTriggerAreaOption.setSubTitle(getTextBoomTriggerAreaSubtitle());
        bindDebugPresetViews();
        updateWarmUpStatus();
        mAdapter.notifyDataSetChanged();
    }

    private void startWarmUpStatusUpdates() {
        mHandler.removeCallbacks(mWarmUpStatusUpdater);
        mHandler.post(mWarmUpStatusUpdater);
    }

    private void updateWarmUpStatus() {
        if (mWarmUpStatusView == null) {
            return;
        }
        int state = CppJiebaTokenizer.get(this).getWarmUpState();
        int textResId;
        boolean keepPolling = false;
        switch (state) {
            case CppJiebaTokenizer.WARM_UP_RUNNING:
                textResId = R.string.debug_warm_up_status_running;
                keepPolling = true;
                break;
            case CppJiebaTokenizer.WARM_UP_READY:
                textResId = R.string.debug_warm_up_status_ready;
                break;
            case CppJiebaTokenizer.WARM_UP_FAILED:
                textResId = R.string.debug_warm_up_status_failed;
                break;
            default:
                textResId = R.string.debug_warm_up_status_idle;
                keepPolling = true;
                break;
        }
        mWarmUpStatusView.setText(textResId);
        mHandler.removeCallbacks(mWarmUpStatusUpdater);
        if (keepPolling) {
            mHandler.postDelayed(mWarmUpStatusUpdater, 250);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTextBoomSwitch.setOnCheckedChangeListener(null);
        mOCRSwitch.setOnCheckedChangeListener(null);
        mHandler.removeCallbacks(mWarmUpStatusUpdater);
        unregisterReceiver(mPackageReceiver);
        if (mOcrSwitchToast != null) {
            mOcrSwitchToast.cancel();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mTextBoomSwitch.getSwitch()) {
            mSettings.setBigBangEnabled(isChecked);
            updateViews();
        } else if (buttonView == mOCRSwitch.getSwitch()) {
            mSettings.setOcrEnabled(isChecked);
        }
    }

    private class BigBangItem {
        public int category;
        public String title;
        public Drawable icon;
        public int settingsValue;
    }

    class OptionsAdapter extends BaseAdapter implements StickyListHeadersAdapter, SectionIndexer {

        private LayoutInflater mLayoutInflater;
        public OptionsAdapter(Context context) {
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mBigBangItemList.size();
        }

        @Override
        public BigBangItem getItem(int position) {
            return mBigBangItemList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SettingItemCheck holder;
            if (convertView == null || !(convertView.getTag() instanceof SettingItemCheck)) {
                convertView = holder = new SettingItemCheck(TextBoomSettingsActivity.this);
                convertView.setTag(holder);
            } else {
                holder = (SettingItemCheck) convertView.getTag();
            }

            BigBangItem item = mBigBangItemList.get(position);
            holder.setTitle(item.title);
            holder.setIcon(item.icon);
            if (item.category == CATEGORY_SEARCH) {
                holder.setChecked(item.settingsValue == mCurrentSearchValue);
            } else if (item.category == CATEGORY_DICT) {
                holder.setChecked(item.settingsValue == mCurrentDictValue);
            }

            if (isFirstOfSection(position)) {
                holder.setBackgroundResource(R.drawable.selector_setting_sub_item_bg_top);
            } else if (isLastOfSection(position)) {
                holder.setBackgroundResource(R.drawable.selector_setting_sub_item_bg_bottom);
            } else {
                holder.setBackgroundResource(R.drawable.selector_setting_sub_item_bg_middle);
            }

            return convertView;
        }

        private boolean isFirstOfSection(int position) {
            int section = getSectionForPosition(position);
            int sectionPos = getPositionForSection(section);
            return position == sectionPos;
        }

        private boolean isLastOfSection(int position) {
            int section = getSectionForPosition(position);
            int nexSectionPos = getPositionForSection(section + 1);
            return position + 1 == nexSectionPos || position + 1 == getCount();
        }

        @Override
        public Object[] getSections() {
            return null;
        }

        @Override
        public int getPositionForSection(int sectionIndex) {
            for (int i = 0; i < mBigBangItemList.size(); i++) {
                if (mBigBangItemList.get(i).category == sectionIndex) {
                    return i;
                }
            }
            return 0;
        }

        @Override
        public int getSectionForPosition(int position) {
            return mBigBangItemList.get(position).category;
        }

        @Override
        public View getHeaderView(int position, View convertView, ViewGroup parent) {
            if (convertView == null || convertView.getTag() == null) {
                convertView = mLayoutInflater.inflate(R.layout.header_text_layout, null);
                TextView itemView = (TextView) convertView.findViewById(R.id.header_text);
                convertView.setTag(itemView);
            }
            TextView headerItemView = ((TextView) convertView.getTag());
            if (getSectionForPosition(position) == CATEGORY_DICT) {
                headerItemView.setText(R.string.default_dict);
                headerItemView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.bigbang_dict, 0);
            } else {
                headerItemView.setText(R.string.default_search_way);
                headerItemView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.bigbang_search, 0);
            }
            return convertView;
        }

        @Override
        public long getHeaderId(int position) {
            return mBigBangItemList.get(position).category;
        }
    }

    private String getTextBoomTriggerAreaSubtitle() {
        String[] options = getResources().getStringArray(R.array.thumb_trigger_area_options);
        int triggerAreaValue = getTextBoomTriggerAreaSettings();
        if (triggerAreaValue < options.length) {
            return options[triggerAreaValue];
        }
        return null;
    }

    private int getTextBoomTriggerAreaSettings() {
        return mSettings.getTriggerArea();
    }

    private void launchTextBoomTriggerAreaOptions() {
        OptionsInfo opts = new OptionsInfo(
                getResources().getStringArray(R.array.thumb_trigger_area_options),
                new Integer[]{BigBangSettings.TRIGGER_AREA_SMALLEST,
                        BigBangSettings.TRIGGER_AREA_SMALL,
                        BigBangSettings.TRIGGER_AREA_MIDDLE,
                        BigBangSettings.TRIGGER_AREA_LARGE,
                        BigBangSettings.TRIGGER_AREA_LARGEST},
                OptionsInfo.SaveTargetTable.App,
                BigBangSettings.KEY_TRIGGER_AREA);

        Intent intent = new Intent(this, OptionsActivity.class);
        intent.putExtra(OptionsActivity.EXTRA_OPTION_INFO, opts);
        intent.putExtra(OptionsActivity.EXTRA_CURRENT_VALUE, String.valueOf(getTextBoomTriggerAreaSettings()));
        intent.putExtra(Title.EXTRA_TITLE_TEXT, getString(R.string.thumb_trigger_area));
        intent.putExtra(Title.EXTRA_BACK_BTN_RES_ID, R.string.text_boom_settings);
        startActivity(intent);
    }

    private void bindDebugPresetViews() {
        if (mDebugPresetSpinner == null || mDebugTextInput == null || mDebugPreviewButton == null) {
            return;
        }
        String[] presets = getResources().getStringArray(R.array.debug_preset_texts);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDebugPresetSpinner.setAdapter(adapter);
        String storedText = mSettings.getDebugPreviewText();
        int selectedIndex = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(storedText)) {
                selectedIndex = i;
                break;
            }
        }
        mDebugPresetSpinner.setSelection(selectedIndex, false);
        mDebugTextInput.setText(storedText);
        mDebugTextInput.setSelection(mDebugTextInput.getText().length());
        mDebugPresetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String text = (String) parent.getItemAtPosition(position);
                mSettings.setDebugPresetText(text);
                mDebugTextInput.setText(text);
                mDebugTextInput.setSelection(text.length());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mDebugPreviewButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openBigBangPreview(mDebugTextInput.getText().toString());
            }
        });
    }

    private void openBigBangPreview(String text) {
        if (text == null) {
            text = "";
        }
        mSettings.setDebugPreviewText(text);
        Intent intent = new Intent(this, BoomActivity.class);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.putExtra(BoomActivity.EXTRA_DEBUG_PREVIEW_TEXT, text);
        intent.putExtra("boom_index", -1);
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        intent.putExtra("boom_startx", width / 2);
        intent.putExtra("boom_starty", height / 2);
        startActivity(intent);
    }

}
