package cc.wuersan008.zhusheng;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.List;

/** Renders one {@link CertSource} per row. */
public class SourceAdapter extends BaseAdapter {

    private final Context context;
    private final List<CertSource> sources;
    private final LayoutInflater inflater;
    /** Color the check boxes are tinted with, or {@link AppColors#DEFAULT}. */
    private final int checkTint;

    public SourceAdapter(Context context, List<CertSource> sources, int checkTint) {
        this.context = context;
        this.sources = sources;
        this.inflater = LayoutInflater.from(context);
        this.checkTint = checkTint;
    }

    public int getCount() {
        return sources.size();
    }

    public Object getItem(int position) {
        return sources.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.row_source, parent, false);
        }

        CertSource source = sources.get(position);

        CheckBox check = (CheckBox) view.findViewById(R.id.row_check);
        TextView name = (TextView) view.findViewById(R.id.row_name);
        TextView url = (TextView) view.findViewById(R.id.row_url);
        TextView state = (TextView) view.findViewById(R.id.row_state);

        check.setChecked(source.selected);
        tint(check);
        name.setText(source.recommended
                ? source.name + context.getString(R.string.recommended_tag)
                : source.name);
        url.setText(source.url);
        state.setText(stateText(source));
        state.setTextColor(stateColor(source));
        return view;
    }

    /**
     * Tints the check box with the accent color of the Holo theme.
     *
     * <p>Holo has no color attribute that the framework widgets would read, so the
     * tint has to be applied per view: through the tint API from API 21 onward, and
     * through a color filter before that (see
     * {@link AppColors#tintButtonGraphic}).</p>
     */
    private void tint(CheckBox check) {
        if (checkTint != AppColors.DEFAULT) {
            AppColors.tintButtonGraphic(check, checkTint);
        }
    }

    private String stateText(CertSource source) {
        switch (source.state) {
            case CertSource.STATE_DOWNLOADING:
                return context.getString(R.string.state_downloading);
            case CertSource.STATE_DONE:
                return context.getString(R.string.state_ok, source.certCount);
            case CertSource.STATE_ERROR:
                return context.getString(R.string.state_error, source.message);
            default:
                return source.note;
        }
    }

    private int stateColor(CertSource source) {
        switch (source.state) {
            case CertSource.STATE_DONE:
                return context.getResources().getColor(R.color.zs_ok);
            case CertSource.STATE_ERROR:
                return context.getResources().getColor(R.color.zs_error);
            default:
                return context.getResources().getColor(R.color.zs_text_dim);
        }
    }
}
