package com.hyperexternal.collapsiblecalendarview.data

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.hyperexternal.collapsiblecalendarview.R
import com.hyperexternal.collapsiblecalendarview.dipToPixels
import com.hyperexternal.collapsiblecalendarview.drawable.CircleDrawable
import java.util.*
import kotlin.collections.ArrayList


/**
 * Created by shrikanthravi on 06/03/18.
 */

class CalendarAdapter(val context: Context, cal: Calendar) {
    private var mFirstDayOfWeek = 0
    var calendar: Calendar
    private val mInflater: LayoutInflater

    private val mItemList = ArrayList<Day>()
    private val mViewList = ArrayList<View>()
    var mEventList: MutableSet<Event> = mutableSetOf()

    var params: Params? = null
    /**
     * Builder pattern is being used so that more things can be added at later stage
     */
    fun setParams(params: Params): CalendarAdapter {
        this.params = params
        return this
    }

    data class Params(val color: Int, val size: Int)

    // public methods
    val count: Int
        get() = mItemList.size

    init {
        this.calendar = cal.clone() as Calendar
        this.calendar.set(Calendar.DAY_OF_MONTH, 1)

        mInflater = LayoutInflater.from(context)

        refresh()
    }

    fun getItem(position: Int): Day {
        return mItemList[position]
    }

    fun getView(position: Int): View {
        return mViewList[position]
    }

    fun setFirstDayOfWeek(firstDayOfWeek: Int) {
        mFirstDayOfWeek = firstDayOfWeek
    }

    fun addEvent(event: Event) {
        mEventList.add(event)
    }

    fun addAllEvents(eventList: MutableList<Event>) {
        this.mEventList = eventList.toMutableSet()
    }

    fun refresh() {
        // clear data
        mItemList.clear()
        mViewList.clear()

        // set calendar
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        calendar.set(year, month, 1)

        val lastDayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

        // generate day list
        val offset = 0 - (firstDayOfWeek - mFirstDayOfWeek) + 1
        val length = Math.ceil(((lastDayOfMonth - offset + 1).toFloat() / 7).toDouble()).toInt() * 7
        for (i in offset until length + offset) {
            val numYear: Int
            val numMonth: Int
            val numDay: Int

            val tempCal = Calendar.getInstance()
            if (i <= 0) { // prev month
                if (month == 0) {
                    numYear = year - 1
                    numMonth = 11
                } else {
                    numYear = year
                    numMonth = month - 1
                }
                tempCal.set(numYear, numMonth, 1)
                numDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH) + i
            } else if (i > lastDayOfMonth) { // next month
                if (month == 11) {
                    numYear = year + 1
                    numMonth = 0
                } else {
                    numYear = year
                    numMonth = month + 1
                }
                tempCal.set(numYear, numMonth, 1)
                numDay = i - lastDayOfMonth
            } else {
                numYear = year
                numMonth = month
                numDay = i
            }

            val day = Day(numYear, numMonth, numDay)

            val view = mInflater.inflate(R.layout.day_layout, null)
            val txtDay = view.findViewById<View>(R.id.txt_day) as TextView
            val parent = txtDay.getParent() as View
            parent.post {
                val r = Rect()
                txtDay.getHitRect(r)
                r.top -= context.dipToPixels(15).toInt()
                r.bottom += context.dipToPixels(15).toInt()
                r.left -= context.dipToPixels(15).toInt()
                r.right += context.dipToPixels(15).toInt()
                parent.touchDelegate = TouchDelegate(r, txtDay)
            }
            val imgEventTag = view.findViewById<View>(R.id.img_event_tag) as ImageView
            params?.let {
                imgEventTag.setImageDrawable(CircleDrawable(context).setParams(CircleDrawable.Params(it.color, it.size)).getCircle())
            }
            txtDay.text = day.day.toString()
            if (day.month != calendar.get(Calendar.MONTH)) {
                txtDay.alpha = 0.3f
            }
            if (mEventList.contains(Event(day.year,day.month, day.day))) {
                imgEventTag.visibility = View.VISIBLE
//                if (params == null) {
//                    //imgEventTag.setColorFilter(day.color, PorterDuff.Mode.SRC_ATOP)
//                }
            }

            mItemList.add(day)
            mViewList.add(view)
        }
    }
}
