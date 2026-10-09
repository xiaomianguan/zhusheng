package cc.wuersan008.zhusheng;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.ScrollView;

/**
 * The page of the main screen: a column of views of which the flexible one is the list
 * of sources.
 *
 * <p>As long as the list can be given the minimum height it asks for, the page is
 * exactly as tall as the screen and never moves. The list then takes the room that is left
 * over - the screen less the header above it, the progress bar and the status line below
 * it, and the buttons under those (see res/layout/activity_main.xml) - and scrolls inside
 * it, while the header, the status line and the buttons stay where they are.</p>
 *
 * <p>When the room left over is less than that minimum - a 320x480 phone, or a display
 * where the header, the progress bar, the status line and the buttons alone leave the
 * list less than it asks for - the page keeps a height of its own and scrolls, but only
 * by the little that is over: the list is still held at that minimum, a few rows, and
 * scrolls inside itself, rather than the page growing tall enough to show every row of
 * the list with the buttons below it.</p>
 *
 * <p>A drag goes to whichever of the two has something to scroll: the page takes a drag
 * that started on the list only once the list has nothing left to scroll that way.</p>
 */
public class PageScrollView extends ScrollView {

    /** Whether the touch in progress started on the list of sources. */
    private boolean touchInList;

    /** Where that touch started, in the coordinates of this page. */
    private float touchDownY;

    public PageScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // A ScrollView lets its descendants take the focus before itself, which here
        // means the list: on a device that is not in touch mode the page would then
        // open scrolled down to the list, with the header out of sight. Let the page
        // take the focus instead. The keys then scroll it, and move the focus into the
        // list once the page has nothing left to scroll.
        setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (getChildCount() == 0) {
            return;
        }
        final View list = findViewById(R.id.list_sources);
        if (!(list instanceof PageListView)) {
            return;
        }
        final View content = getChildAt(0);
        final int page = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

        // Whether the list scrolls inside itself, that is, whether it is the one that
        // has to be given the focus and the keys.
        boolean listScrolls = false;

        if (content.getMeasuredHeight() > page) {
            // Everything at the height it asks for does not fit on the page, so the
            // list is the one that gives way: it is given the room that is left over,
            // or the minimum it asks for when there is less than that, and scrolls
            // inside itself. The page is left the little that is still over - a few
            // rows' worth at most - instead of growing tall enough to show the whole
            // list with the buttons below it.
            final int fixed = content.getMeasuredHeight() - list.getMeasuredHeight();
            final int minimum = ((PageListView) list).getMinimumListHeight();
            final int room = Math.max(minimum, page - fixed);
            if (room < list.getMeasuredHeight()) {
                // The width has to be the one this page gives the content, which is the
                // page less its padding - the same one the framework used when it
                // measured the content itself. This view's own spec is the page with the
                // padding still in it, and a content measured with that is wider than the
                // page can show: its right edge, where the buttons end, is cut off.
                final int width = MeasureSpec.getSize(widthMeasureSpec)
                        - getPaddingLeft() - getPaddingRight();
                content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(fixed + room, MeasureSpec.EXACTLY));
                listScrolls = true;
            }
            // A list that fits in its minimum whole is left as it is: there is nothing
            // to be gained by giving it less room than it asks for.
        }

        // A list that shows every row and cannot scroll must not be focusable: it would
        // keep the keys to itself and leave the buttons below it out of reach of them.
        if (list.isFocusable() != listScrolls) {
            list.setFocusable(listScrolls);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // A page with nothing to scroll must not take the drags away from the list
        // inside it, which does scroll then.
        if (!canScroll()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchInList = isOverList(event);
                touchDownY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (touchInList && listScrollsBy(touchDownY - event.getY())) {
                    // The drag is one the list scrolls with: the list keeps it.
                    return false;
                }
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(event);
    }

    /** Whether the content is taller than the page, that is, whether the page scrolls. */
    private boolean canScroll() {
        return getChildCount() > 0
                && getChildAt(0).getHeight() > getHeight() - getPaddingTop() - getPaddingBottom();
    }

    /** Whether the touch landed on the list of sources. */
    private boolean isOverList(MotionEvent event) {
        final View list = findViewById(R.id.list_sources);
        if (list == null || list.getVisibility() != VISIBLE) {
            return false;
        }
        // On the screen, which is where the raw coordinates of the touch are: that
        // takes in where the list sits on the page and how far either has scrolled.
        final int[] position = new int[2];
        list.getLocationOnScreen(position);
        final float x = event.getRawX();
        final float y = event.getRawY();
        return x >= position[0] && x < position[0] + list.getWidth()
                && y >= position[1] && y < position[1] + list.getHeight();
    }

    /** Whether the list scrolls when the finger moves by {@code dy} pixels. */
    private boolean listScrollsBy(float dy) {
        final View view = findViewById(R.id.list_sources);
        if (!(view instanceof ListView)) {
            return false;
        }
        final ListView list = (ListView) view;
        if (dy > 0) {
            // The finger moved up, so the list scrolls down and needs rows below it.
            return list.getLastVisiblePosition() < list.getCount() - 1;
        }
        if (dy < 0) {
            return list.getFirstVisiblePosition() > 0;
        }
        return false;
    }
}
