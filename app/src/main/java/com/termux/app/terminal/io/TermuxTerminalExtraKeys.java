package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    /*
     * Handler لتقسيم اللصق الكبير إلى دفعات صغيرة.
     */
    private final Handler mPasteHandler =
        new Handler(Looper.getMainLooper());

    private Runnable mTurboPasteRunnable;

    /*
     * حجم كل دفعة وعدد المللي ثانية بين الدفعات.
     */
    private static final int TURBO_PASTE_CHUNK_SIZE = 4096;
    private static final long TURBO_PASTE_DELAY_MS = 8L;

    public TermuxTerminalExtraKeys(
        TermuxActivity activity,
        @NonNull TerminalView terminalView,
        TermuxTerminalViewClient termuxTerminalViewClient,
        TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient
    ) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient =
            termuxTerminalSessionActivityClient;

        setExtraKeys();
    }

    /**
     * تحميل إعدادات أزرار لوحة المفاتيح الإضافية.
     */
    private void setExtraKeys() {

        mExtraKeysInfo = null;

        try {

            String extrakeys =
                (String) mActivity.getProperties()
                    .getInternalPropertyValue(
                        TermuxPropertyConstants.KEY_EXTRA_KEYS,
                        true
                    );

            String extraKeysStyle =
                (String) mActivity.getProperties()
                    .getInternalPropertyValue(
                        TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE,
                        true
                    );

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap =
                ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);

            if (
                ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS
                    .DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap)
                &&
                !TermuxPropertyConstants
                    .DEFAULT_IVALUE_EXTRA_KEYS_STYLE
                    .equals(extraKeysStyle)
            ) {

                Logger.logError(
                    TermuxSharedProperties.LOG_TAG,
                    "Invalid extra keys style: " + extraKeysStyle
                );

                extraKeysStyle =
                    TermuxPropertyConstants
                        .DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(
                extrakeys,
                extraKeysStyle,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES
            );

        } catch (JSONException e) {

            Logger.showToast(
                mActivity,
                "Could not load extra keys: " + e,
                true
            );

            Logger.logStackTraceWithMessage(
                LOG_TAG,
                "Could not load extra keys: ",
                e
            );

            try {

                mExtraKeysInfo = new ExtraKeysInfo(
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
                    TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE,
                    ExtraKeysConstants.CONTROL_CHARS_ALIASES
                );

            } catch (JSONException e2) {

                Logger.showToast(
                    mActivity,
                    "Could not create default extra keys",
                    true
                );

                Logger.logStackTraceWithMessage(
                    LOG_TAG,
                    "Could not create default extra keys: ",
                    e2
                );

                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    /**
     * التعامل مع الضغط على أزرار لوحة المفاتيح الإضافية.
     */
    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(
        View view,
        String key,
        boolean ctrlDown,
        boolean altDown,
        boolean shiftDown,
        boolean fnDown
    ) {

        /*
         * إظهار أو إخفاء لوحة المفاتيح.
         */
        if ("KEYBOARD".equals(key)) {

            if (mTermuxTerminalViewClient != null) {
                mTermuxTerminalViewClient
                    .onToggleSoftKeyboardRequest();
            }

        /*
         * فتح أو إغلاق القائمة الجانبية.
         */
        } else if ("DRAWER".equals(key)) {

            if (mTermuxTerminalViewClient == null) {
                return;
            }

            DrawerLayout drawerLayout =
                mTermuxTerminalViewClient.getActivity().getDrawer();

            if (drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                drawerLayout.closeDrawer(Gravity.LEFT);
            } else {
                drawerLayout.openDrawer(Gravity.LEFT);
            }

        /*
         * PASTE:
         * استخدام آلية اللصق الأصلية في Termux.
         */
        } else if ("PASTE".equals(key)) {

            if (mTermuxTerminalSessionActivityClient != null) {

                mTermuxTerminalSessionActivityClient
                    .onPasteTextFromClipboard(null);
            }

        /*
         * COPY ALL:
         * نسخ سجل الطرفية إلى الحافظة.
         */
        } else if ("COPYALL".equals(key)) {

            if (
                mActivity.getCurrentSession() != null
                &&
                mActivity.getCurrentSession().getEmulator() != null
            ) {

                String text =
                    mActivity.getCurrentSession()
                        .getEmulator()
                        .getScreen()
                        .getTranscriptText();

                ClipboardManager clipboard =
                    (ClipboardManager) mActivity.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    );

                if (clipboard != null) {

                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Termux terminal",
                            text
                        )
                    );

                    Toast.makeText(
                        mActivity,
                        "Terminal text copied",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }

        /*
         * TURBO:
         * لصق النص الموجود في الحافظة على دفعات صغيرة.
         * لا يضيف Enter تلقائيًا.
         */
        } else if ("TURBO".equals(key)) {

            pasteClipboardInChunks(false);

        /*
         * PASTE+ENTER:
         * لصق النص ثم إرسال Enter بعد انتهاء جميع الدفعات.
         */
        } else if ("PASTEENTER".equals(key)) {

            pasteClipboardInChunks(true);

        /*
         * STOP:
         * إلغاء الدفعات المتبقية وإرسال Ctrl+C للطرفية.
         */
        } else if ("STOP".equals(key)) {

            cancelTurboPaste();

            if (mActivity.getCurrentSession() != null) {

                mActivity.getCurrentSession().write("\u0003");

                Toast.makeText(
                    mActivity,
                    "Paste stopped; Ctrl+C sent",
                    Toast.LENGTH_SHORT
                ).show();

            } else {

                Toast.makeText(
                    mActivity,
                    "No active terminal session",
                    Toast.LENGTH_SHORT
                ).show();
            }

        /*
         * SCROLL:
         * تبديل وضع التمرير التلقائي.
         */
        } else if ("SCROLL".equals(key)) {

            if (mTermuxTerminalViewClient == null) {
                return;
            }

            TerminalView terminalView =
                mTermuxTerminalViewClient
                    .getActivity()
                    .getTerminalView();

            if (
                terminalView != null
                &&
                terminalView.mEmulator != null
            ) {

                terminalView.mEmulator
                    .toggleAutoScrollDisabled();
            }

        } else {

            super.onTerminalExtraKeyButtonClick(
                view,
                key,
                ctrlDown,
                altDown,
                shiftDown,
                fnDown
            );
        }
    }

    /**
     * قراءة النص من الحافظة وبدء اللصق المقسّم.
     *
     * @param pressEnter هل يتم إرسال Enter بعد اكتمال اللصق؟
     */
    private void pasteClipboardInChunks(boolean pressEnter) {

        ClipboardManager clipboard =
            (ClipboardManager) mActivity.getSystemService(
                Context.CLIPBOARD_SERVICE
            );

        if (
            clipboard == null
            ||
            !clipboard.hasPrimaryClip()
            ||
            clipboard.getPrimaryClip() == null
            ||
            clipboard.getPrimaryClip().getItemCount() == 0
        ) {

            Toast.makeText(
                mActivity,
                "Clipboard is empty",
                Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (mActivity.getCurrentSession() == null) {

            Toast.makeText(
                mActivity,
                "No active terminal session",
                Toast.LENGTH_SHORT
            ).show();

            return;
        }

        CharSequence clipboardText =
            clipboard.getPrimaryClip()
                .getItemAt(0)
                .coerceToText(mActivity);

        if (clipboardText == null) {
            return;
        }

        startTurboPaste(
            clipboardText.toString(),
            pressEnter
        );
    }

    /**
     * لصق النص تدريجيًا لتجنب إرسال النص كاملًا في عملية واحدة.
     */
    private void startTurboPaste(
        final String text,
        final boolean pressEnter
    ) {

        cancelTurboPaste();

        if (
            text == null
            ||
            text.isEmpty()
            ||
            mActivity.getCurrentSession() == null
        ) {
            return;
        }

        final int[] offset = {0};

        mTurboPasteRunnable = new Runnable() {

            @Override
            public void run() {

                /*
                 * إذا لم تعد هناك جلسة نشطة، أوقف العملية.
                 */
                if (mActivity.getCurrentSession() == null) {

                    mTurboPasteRunnable = null;
                    return;
                }

                /*
                 * تحديد نهاية الدفعة الحالية.
                 */
                int end = Math.min(
                    offset[0] + TURBO_PASTE_CHUNK_SIZE,
                    text.length()
                );

                /*
                 * إرسال الدفعة الحالية.
                 */
                if (end > offset[0]) {

                    mActivity.getCurrentSession().write(
                        text.substring(offset[0], end)
                    );

                    offset[0] = end;
                }

                /*
                 * إذا تبقى نص، جدولة الدفعة التالية.
                 */
                if (offset[0] < text.length()) {

                    mPasteHandler.postDelayed(
                        this,
                        TURBO_PASTE_DELAY_MS
                    );

                } else {

                    /*
                     * إرسال Enter فقط بعد اكتمال اللصق
                     * إذا كان الزر هو PASTE+ENTER.
                     */
                    if (
                        pressEnter
                        &&
                        mActivity.getCurrentSession() != null
                    ) {

                        mActivity.getCurrentSession().write("\n");
                    }

                    mTurboPasteRunnable = null;

                    Toast.makeText(
                        mActivity,
                        "Paste complete",
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        };

        /*
         * بدء اللصق.
         */
        mPasteHandler.post(mTurboPasteRunnable);
    }

    /**
     * إلغاء عملية اللصق المجدولة.
     */
    private void cancelTurboPaste() {

        if (mTurboPasteRunnable != null) {

            mPasteHandler.removeCallbacks(
                mTurboPasteRunnable
            );

            mTurboPasteRunnable = null;
        }
    }
}
