package cc.wuersan008.zhusheng;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * The list of sources, sized for a page that scrolls rather than for scrolling itself.
 *
 * <p>A list laid out inside a scrolling page is measured with no height limit, and the
 * framework answers that with the height of a single row: a plain {@code ListView} in a
 * {@code ScrollView} is one row tall. That is what makes the list unusable on a small
 * screen. This list answers with the height of all of its rows instead, so that the page
 * around it knows what it is dealing with; the page then gives the list the room it has,
 * and holds it at the minimum below when there is less than that (see
 * {@link PageScrollView}).</p>
 */
public class PageListView extends ListView {

    /**
     * The largest height a measure spec can carry. Used to ask for the height of all
     * rows at once: the framework measures the rows for an at-most height and stops
     * when it runs out of room, so it is given room for everything.
     */
    private static final int UNLIMITED = Integer.MAX_VALUE >> 2;

    /** How many rows the list is never squeezed below. */
    private static final int MINIMUM_ROWS = 2;

    /** The height of {@link #MINIMUM_ROWS} rows, measured in {@link #onMeasure}. */
    private int minimumHeight;

    public PageListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * The height below which this list must not be squeezed: {@link #MINIMUM_ROWS} of
     * its rows, or the {@code android:minHeight} of the layout when that is more.
     *
     * <p>A list ignores that minimum itself, so the page around it is the one that
     * honors it, by scrolling the whole screen instead of giving the list less room
     * than this.</p>
     */
    public int getMinimumListHeight() {
        return Math.max(getSuggestedMinimumHeight(), minimumHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureMinimumHeight(widthMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            // No height limit: report the height of every row.
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(UNLIMITED, MeasureSpec.AT_MOST));
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Measures the first row to learn how tall a row is, and remembers {@link
     * #MINIMUM_ROWS} of them.
     *
     * <p>Measured here rather than declared in the layout because how tall a row is is
     * up to the text in it: a name that wraps, a longer translation or a denser screen
     * makes every row taller, and the minimum follows. Only the width comes from the
     * layout, since that is what decides where the text wraps.</p>
     */
    private void measureMinimumHeight(int widthMeasureSpec) {
        final ListAdapter adapter = getAdapter();
        final int width = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        if (adapter == null || adapter.getCount() == 0 || width <= 0) {
            // Nothing to measure against yet; the next pass knows both.
            minimumHeight = 0;
            return;
        }
        final View row = adapter.getView(0, null, this);
        if (row == null) {
            minimumHeight = 0;
            return;
        }
        row.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        minimumHeight = row.getMeasuredHeight() * MINIMUM_ROWS;
    }
}
