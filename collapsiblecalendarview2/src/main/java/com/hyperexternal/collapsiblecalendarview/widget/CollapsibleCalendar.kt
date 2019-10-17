package com.hyperexternal.collapsiblecalendarview.widget

/**
 * Created by shrikanthravi on 07/03/18.
 */


import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.hyperexternal.collapsiblecalendarview.data.CalendarAdapter
import com.hyperexternal.collapsiblecalendarview.data.Day
import com.hyperexternal.collapsiblecalendarview.data.Event
import com.hyperexternal.collapsiblecalendarview.dipToPixels
import com.hyperexternal.collapsiblecalendarview.view.ExpandIconView
import java.lang.Math.abs
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class CollapsibleCalendar : UICalendar, View.OnClickListener {
    override fun changeToToday() {
        val calendar = Calendar.getInstance()
        val calenderAdapter = CalendarAdapter(context, calendar)
        this.params?.let {
            calenderAdapter.setParams(CalendarAdapter.Params(it.eventColor, it.eventSize))
        }
        calenderAdapter.mEventList = mAdapter!!.mEventList
        calenderAdapter.setFirstDayOfWeek(firstDayOfWeek)
        val today = GregorianCalendar()
        this.selectedItem = null
        this.selectedItemPosition = -1
        this.selectedDay = Day(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
        mCurrentWeekIndex = suitableRowIndex
        setAdapter(calenderAdapter)
        mListener?.onTodayClick()
    }

    override fun onClick(view: View?) {
        view?.let {
            mListener.let { mListener ->
                if (mListener == null) {
                    expandIconView.performClick()
                } else {
                    mListener.onClickListener()
                }
            }
        }
    }

    private var mAdapter: CalendarAdapter? = null
    private var mListener: CalendarListener? = null

    var expanded = false

    private var mInitHeight = 0

    private val mHandler = Handler()
    private var mIsWaitingForUpdate = false

    private var mCurrentWeekIndex: Int = 0

    private val suitableRowIndex: Int
        get() {
            if (selectedItemPosition != -1) {
                val view = mAdapter!!.getView(selectedItemPosition)
                val row = view.parent as TableRow

                return mTableBody.indexOfChild(row)
            } else if (todayItemPosition != -1) {
                val view = mAdapter!!.getView(todayItemPosition)
                val row = view.parent as TableRow

                return mTableBody.indexOfChild(row)
            } else {
                return 0
            }
        }

    val year: Int
        get() = mAdapter!!.calendar.get(Calendar.YEAR)

    val month: Int
        get() = mAdapter!!.calendar.get(Calendar.MONTH)

    /**
     * The date has been selected and can be used on Calender Listener
     */
    var selectedDay: Day? = null
        get() {
            if (selectedItem == null) {
                val cal = Calendar.getInstance()
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val month = cal.get(Calendar.MONTH)
                val year = cal.get(Calendar.YEAR)
                return Day(
                        year,
                        month + 1,
                        day)
            }
            return Day(
                    selectedItem!!.year,
                    selectedItem!!.month,
                    selectedItem!!.day)
        }
        set(value: Day?) {
            field = value
            redraw()
        }

    var selectedItemPosition: Int = -1
        get() {
            var position = -1
            for (i in 0 until mAdapter!!.count) {
                val day = mAdapter!!.getItem(i)

                if (isSelectedDay(day)) {
                    position = i
                    break
                }
            }
            if (position == -1) {
                position = todayItemPosition
            }
            return position
        }

    val todayItemPosition: Int
        get() {
            var position = -1
            for (i in 0 until mAdapter!!.count) {
                val day = mAdapter!!.getItem(i)

                if (isToday(day)) {
                    position = i
                    break
                }
            }
            return position
        }

    override var state: Int
        get() = super.state
        set(state) {
            super.state = state
            if (state == STATE_COLLAPSED) {
                expanded = false
            }
            if (state == STATE_EXPANDED) {
                expanded = true
            }
        }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    protected fun init(context: Context) {


        val cal = Calendar.getInstance()
        val adapter = CalendarAdapter(context, cal)
        setAdapter(adapter)


        // bind events

        mBtnPrevMonth.setOnClickListener { prevMonth() }

        mBtnNextMonth.setOnClickListener { nextMonth() }

        mBtnPrevWeek.setOnClickListener { prevWeek() }

        mBtnNextWeek.setOnClickListener { nextWeek() }

        todayFrameLayout.setOnClickListener { changeToToday() }

        filterFrameLayout.setOnClickListener { filterClicked() }

        expandIconView.setState(ExpandIconView.MORE, true)


        expandIconView.setOnClickListener {
            if (expanded) {
                collapse(400)
            } else {
                expand(400)
            }
        }

        linearExpandCollapse.setOnClickListener { expandIconView.performClick() }

    }

    private fun filterClicked() {
        mListener?.onFilterClick()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        mInitHeight = mTableBody.measuredHeight

        if (mIsWaitingForUpdate) {
            redraw()
            mHandler.post { collapseTo(mCurrentWeekIndex) }
            mIsWaitingForUpdate = false
            if (mListener != null) {
                mListener!!.onDataUpdate()
            }
        }
    }

    override fun redraw() {
        // redraw all views of week
        val rowWeek = mTableHead.getChildAt(0) as TableRow?
        if (rowWeek != null) {
            for (i in 0 until rowWeek.childCount) {
                (rowWeek.getChildAt(i) as TextView).setTextColor(textColor)
            }
        }
        // redraw all views of day
        if (mAdapter != null) {
            for (i in 0 until mAdapter!!.count) {
                val day = mAdapter!!.getItem(i)
                val view = mAdapter!!.getView(i)
                val txtDay = view.findViewById<View>(com.hyperexternal.collapsiblecalendarview.R.id.txt_day) as TextView
                txtDay.setBackgroundColor(Color.TRANSPARENT)
                txtDay.setTextColor(textColor)

                // set today's item
                if (isToday(day)) {
                    txtDay.setBackgroundDrawable(todayItemBackgroundDrawable)
                    txtDay.setTextColor(todayItemTextColor)
                }

                // set the selected item
                if (isSelectedDay(day)) {
                    txtDay.setBackgroundDrawable(selectedItemBackgroundDrawable)
                    txtDay.setTextColor(selectedItemTextColor)
                }
            }
        }
    }

    override fun reload() {
        mAdapter?.let { mAdapter ->
            mAdapter.refresh()
            val calendar = Calendar.getInstance()
            val tempDatePattern: String
            if (calendar.get(Calendar.YEAR) != mAdapter.calendar.get(Calendar.YEAR)) {
                tempDatePattern = "MMMM YYYY"
            } else {
                tempDatePattern = datePattern
            }
            // reset UI
            val dateFormat = SimpleDateFormat(tempDatePattern, getCurrentLocale(context))
            dateFormat.timeZone = mAdapter.calendar.timeZone
            mTxtTitle.text = dateFormat.format(mAdapter.calendar.time)
            mTableHead.removeAllViews()
            mTableBody.removeAllViews()

            var rowCurrent: TableRow
            rowCurrent = TableRow(context)
            rowCurrent.layoutParams = TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            for (i in 0..6) {
                val view = mInflater.inflate(com.hyperexternal.collapsiblecalendarview.R.layout.layout_day_of_week, null)
                val txtDayOfWeek = view.findViewById<View>(com.hyperexternal.collapsiblecalendarview.R.id.txt_day_of_week) as TextView
                txtDayOfWeek.setText(DateFormatSymbols().getShortWeekdays()[(i + firstDayOfWeek) % 7 + 1])
                view.layoutParams = TableRow.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f)
                rowCurrent.addView(view)
            }
            mTableHead.addView(rowCurrent)

            // set day view
            for (i in 0 until mAdapter.count) {

                if (i % 7 == 0) {
                    rowCurrent = TableRow(context)
                    rowCurrent.layoutParams = TableLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)
                    mTableBody.addView(rowCurrent)
                }
                val view = mAdapter.getView(i)
                view.layoutParams = TableRow.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f)
                params.let { params ->
                    if (params != null && (mAdapter.getItem(i).diff < params.prevDays || mAdapter.getItem(i).diff > params.nextDaysBlocked)) {
                        view.isClickable = false
                        view.alpha = 0.3f
                    } else {
                        view.setOnClickListener { v -> onItemClicked(v, mAdapter.getItem(i)) }
                    }
                }
                rowCurrent.addView(view)
            }

            redraw()
            mIsWaitingForUpdate = true
            params?.onReload?.invoke()
        }
    }

    fun onItemClicked(view: View, day: Day) {
        select(day)

        val cal = mAdapter!!.calendar

        val newYear = day.year
        val newMonth = day.month
        val oldYear = cal.get(Calendar.YEAR)
        val oldMonth = cal.get(Calendar.MONTH)
        if (newMonth != oldMonth) {
            cal.set(day.year, day.month, 1)

            if (newYear > oldYear || newMonth > oldMonth) {
                mCurrentWeekIndex = 0
            }
            if (newYear < oldYear || newMonth < oldMonth) {
                mCurrentWeekIndex = -1
            }
            if (mListener != null) {
                mListener!!.onMonthChange()
            }
            reload()
        }

        if (mListener != null) {
            mListener!!.onItemClick(view)
        }
    }

    // public methods
    fun setAdapter(adapter: CalendarAdapter) {
        mAdapter = adapter
        adapter.setFirstDayOfWeek(firstDayOfWeek)

        reload()

        // init week
        mCurrentWeekIndex = suitableRowIndex
    }

    fun addEventTag(numYear: Int, numMonth: Int, numDay: Int) {
        mAdapter!!.addEvent(Event(numYear, numMonth, numDay, eventColor))

        reload()
    }

    fun addAllEvent(eventList: List<Event>){
        mAdapter!!.mEventList = eventList.toMutableSet()
        reload()
    }

    fun addEventTag(numYear: Int, numMonth: Int, numDay: Int, color: Int) {
        mAdapter!!.addEvent(Event(numYear, numMonth, numDay, color))


        reload()
    }

    fun prevMonth() {
        val cal = mAdapter!!.calendar
        params.let {
            if (it != null && (Calendar.getInstance().get(Calendar.YEAR) * 12 + Calendar.getInstance().get(Calendar.MONTH) + it.prevDays.toFloat() / 30 +1) > (cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)).toFloat()) {
//                val myAnim = AnimationUtils.loadAnimation(context, R.anim.bounce)
//                val interpolator = BounceAnimator(0.1, 100.0)
//                myAnim.setInterpolator(interpolator)
//                mLayoutRoot.startAnimation(myAnim)
                bounceScrollView.isMoved = true
                return
            }
            if (cal.get(Calendar.MONTH) == cal.getActualMinimum(Calendar.MONTH)) {
                cal.set(cal.get(Calendar.YEAR) - 1, cal.getActualMaximum(Calendar.MONTH), 1)
            } else {
                cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) - 1)
            }
            reload()
            if (mListener != null) {
                mListener!!.onMonthChange()
            }
        }

    }

    fun nextMonth() {
        val cal = mAdapter!!.calendar
        params.let {
            if (it != null && (Calendar.getInstance().get(Calendar.YEAR) * 12 + Calendar.getInstance().get(Calendar.MONTH) + it.nextDaysBlocked / 30.toFloat() -1 ) < (cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)).toFloat()) {
//                val myAnim = AnimationUtils.loadAnimation(context, R.anim.bounce)
//                val interpolator = BounceAnimator(0.1, 100.0)
//                myAnim.setInterpolator(interpolator)
//                this.startAnimation(myAnim)
                bounceScrollView.isMoved = true
                return
            }
            if (cal.get(Calendar.MONTH) == cal.getActualMaximum(Calendar.MONTH)) {
                cal.set(cal.get(Calendar.YEAR) + 1, cal.getActualMinimum(Calendar.MONTH), 1)
            } else {
                cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) + 1)
            }
            reload()
            if (mListener != null) {
                mListener!!.onMonthChange()
            }
        }
    }

    fun nextDay() {
        if (selectedItemPosition == mAdapter!!.count - 1) {
            nextMonth()
            mAdapter!!.getView(0).performClick()
            reload()
            mCurrentWeekIndex = 0
            collapseTo(mCurrentWeekIndex)
        } else {
            mAdapter!!.getView(selectedItemPosition + 1).performClick()
            if (((selectedItemPosition + 1 - mAdapter!!.calendar.firstDayOfWeek) / 7) > mCurrentWeekIndex) {
                nextWeek()
            }
        }
        mListener?.onDayChanged()
    }

    fun prevDay() {
        if (selectedItemPosition == 0) {
            prevMonth()
            mAdapter!!.getView(mAdapter!!.count - 1).performClick()
            reload()
            return;
        } else {
            mAdapter!!.getView(selectedItemPosition - 1).performClick()
            if (((selectedItemPosition - 1 + mAdapter!!.calendar.firstDayOfWeek) / 7) < mCurrentWeekIndex) {
                prevWeek()
            }
        }
        mListener?.onDayChanged()
    }

    fun prevWeek() {
        if (mCurrentWeekIndex - 1 < 0) {
            mCurrentWeekIndex = -1
            prevMonth()
        } else {
            mCurrentWeekIndex--
            collapseTo(mCurrentWeekIndex)
        }
    }

    fun nextWeek() {
        if (mCurrentWeekIndex + 1 >= mTableBody.childCount) {
            mCurrentWeekIndex = 0
            nextMonth()
        } else {
            mCurrentWeekIndex++

            collapseTo(mCurrentWeekIndex)
        }
    }

    fun isSelectedDay(day: Day?): Boolean {
        return (day != null
                && selectedItem != null
                && day.year == selectedItem!!.year
                && day.month == selectedItem!!.month
                && day.day == selectedItem!!.day)
    }

    fun isToday(day: Day?): Boolean {
        val todayCal = Calendar.getInstance()
        return (day != null
                && day.year == todayCal.get(Calendar.YEAR)
                && day.month == todayCal.get(Calendar.MONTH)
                && day.day == todayCal.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * collapse in milliseconds
     */
    open fun collapse(duration: Int) {

        if (state == STATE_EXPANDED) {
            state = STATE_PROCESSING

            mLayoutBtnGroupMonth.visibility = View.GONE
            mLayoutBtnGroupWeek.visibility = View.VISIBLE
            mBtnPrevWeek.isClickable = false
            mBtnNextWeek.isClickable = false

            val index = suitableRowIndex
            mCurrentWeekIndex = index
            mTableHead.setPadding(0,0,0,context.dipToPixels(6).toInt())
            val currentHeight = mInitHeight
            val targetHeight = mTableBody.getChildAt(index).measuredHeight - context.dipToPixels(16).toInt()
            var tempHeight = 0
            for (i in 0 until index) {
                tempHeight += mTableBody.getChildAt(i).measuredHeight
            }
            val topHeight = tempHeight

            val anim = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation) {

                    mScrollViewBody.layoutParams.height = if (interpolatedTime == 1f)
                        targetHeight
                    else
                        currentHeight - ((currentHeight - targetHeight) * interpolatedTime).toInt()
                    mScrollViewBody.requestLayout()

                    if (mScrollViewBody.measuredHeight < topHeight + targetHeight) {
                        val position = topHeight + targetHeight - mScrollViewBody.measuredHeight
                        mScrollViewBody.smoothScrollTo(0, position)
                    }

                    if (interpolatedTime == 1f) {
                        state = STATE_COLLAPSED

                        mBtnPrevWeek.isClickable = true
                        mBtnNextWeek.isClickable = true
                    }
                }
            }
            anim.duration = duration.toLong()
            startAnimation(anim)
        }

        expandIconView.setState(ExpandIconView.MORE, true)
        reload()
    }

    private fun collapseTo(index: Int) {
        var index = index
        if (state == STATE_COLLAPSED) {
            if (index == -1) {
                index = mTableBody.childCount - 1
            }
            mCurrentWeekIndex = index

            mTableHead.setPadding(0,0,0,context.dipToPixels(6).toInt())
            val targetHeight = mTableBody.getChildAt(index).measuredHeight - context.dipToPixels(16).toInt()
            var tempHeight = 0
            for (i in 0 until index) {
                tempHeight += mTableBody.getChildAt(i).measuredHeight
            }
            val topHeight = tempHeight

            mScrollViewBody.layoutParams.height = targetHeight
            mScrollViewBody.requestLayout()

            mHandler.post { mScrollViewBody.smoothScrollTo(0, topHeight) }


            if (mListener != null) {
                mListener!!.onWeekChange(mCurrentWeekIndex)
            }
        }
    }

    fun expand(duration: Int) {
        if (state == STATE_COLLAPSED) {
            state = STATE_PROCESSING

            mLayoutBtnGroupMonth.visibility = View.VISIBLE
            mLayoutBtnGroupWeek.visibility = View.GONE
            mBtnPrevMonth.isClickable = false
            mBtnNextMonth.isClickable = false
            mTableHead.setPadding(0,0,0,context.dipToPixels(30).toInt())
            val currentHeight = mScrollViewBody.measuredHeight
            val targetHeight = mInitHeight

            val anim = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation) {

                    mScrollViewBody.layoutParams.height = if (interpolatedTime == 1f)
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    else
                        currentHeight - ((currentHeight - targetHeight) * interpolatedTime).toInt()
                    mScrollViewBody.requestLayout()

                    if (interpolatedTime == 1f) {
                        state = STATE_EXPANDED

                        mBtnPrevMonth.isClickable = true
                        mBtnNextMonth.isClickable = true
                    }
                }
            }
            anim.duration = duration.toLong()
            startAnimation(anim)
        }

        expandIconView.setState(ExpandIconView.LESS, true)
        reload()
    }

    fun select(day: Day) {
        selectedItem = Day(day.year, day.month, day.day)

        redraw()

        if (mListener != null) {
            mListener!!.onDaySelect()
        }
    }

    fun setStateWithUpdateUI(state: Int) {
        this@CollapsibleCalendar.state = state

        if (state != state) {
            mIsWaitingForUpdate = true
            requestLayout()
        }
    }

    // callback
    fun setCalendarListener(listener: CalendarListener) {
        mListener = listener
    }

    interface CalendarListener {

        // triggered when a day is selected programmatically or clicked by user.
        fun onDaySelect()

        // triggered only when the views of day on calendar are clicked by user.
        fun onItemClick(v: View)

        // triggered when the data of calendar are updated by changing month or adding events.
        fun onDataUpdate()

        // triggered when the month are changed.
        fun onMonthChange()

        // triggered when the week position are changed.
        fun onWeekChange(position: Int)

        fun onClickListener()

        fun onDayChanged()

        fun onFilterClick()

        fun onTodayClick()
    }

    fun setExpandIconVisible(visible: Boolean) {
        if (visible) {
            expandIconView.visibility = View.VISIBLE
        } else {
            expandIconView.visibility = View.GONE
        }
    }

    /**
     *  Idea: The idea behind creating params class instead of attrs, is that it can be maintained easily
     *  prevDays - To block all the days after a certain interval - It has to be negative
     *  TODO: To check the same in future
     *  nextDaysBlocked - To block all the days after a certain days in future (it has to be positive)
     *  filter to enable the filter for the events, we can change the events
     *  TODO: we need to reload after filter is successful but this would be part of the main UI
     *  eventColor - To set the color of the main event
     *  eventSize - To get the size of the icons, we will be getting it from user
     */
    data class Params(val prevDays: Int,
                      val nextDaysBlocked: Int,
                      val filterAvailable: Boolean,
                      val eventColor: Int,
                      val eventSize: Int,
                      val todayDrawable: Drawable? = null,
                      val selectedDrawable: Drawable? = null,
                      val onReload: (()->Unit)? = null)

    var params: Params? = null
        set(value) {
            field = value
            value?.let {
                if (it.filterAvailable) {
                    filterFrameLayout.visibility = View.VISIBLE
                }
                mAdapter?.let {
                    it.setParams(CalendarAdapter.Params(value.eventColor, value.eventSize))
                    reload()
                }
                params?.selectedDrawable?.let {
                    selectedItemBackgroundDrawable = it
                }
                params?.todayDrawable?.let {
                    todayItemBackgroundDrawable = it
                }
            }
        }

    companion object {
        fun daysBetween(startDate: Calendar, endDate: Calendar): Long {
            val end = endDate.timeInMillis
            val start = startDate.timeInMillis
            val differenceMillis = abs(end - start)
            return (differenceMillis / (1000*60*60*24))
        }
    }
}

