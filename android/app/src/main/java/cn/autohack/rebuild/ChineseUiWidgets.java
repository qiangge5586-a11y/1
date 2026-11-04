package cn.autohack.rebuild;

import android.content.Context;

/** Text widgets that render all app-authored copy in Simplified Chinese. */
final class ChineseTextView extends android.widget.TextView {
    ChineseTextView(Context context) { super(context); }
    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(ChineseText.translate(text), type);
    }
}

final class ChineseButton extends android.widget.Button {
    ChineseButton(Context context) { super(context); }
    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(ChineseText.translate(text), type);
    }
}

final class ChineseCheckBox extends android.widget.CheckBox {
    ChineseCheckBox(Context context) { super(context); }
    @Override public void setText(CharSequence text, BufferType type) {
        super.setText(ChineseText.translate(text), type);
    }
}
