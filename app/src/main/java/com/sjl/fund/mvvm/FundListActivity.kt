package com.sjl.fund.mvvm


import android.animation.ValueAnimator
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.listener.OnItemDragListener
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.core.kotlin.mvvm.BaseViewModelActivity
import com.sjl.core.kotlin.util.log.LogUtils
import com.sjl.fund.R
import com.sjl.fund.adapter.FundListAdapter
import com.sjl.fund.db.DaoRepository
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.util.DateUtils
import kotlinx.android.synthetic.main.fund_list_activity.*
import java.text.SimpleDateFormat
import java.util.*


class FundListActivity : BaseViewModelActivity<FundListViewModel>() {

    override fun getLayoutId(): Int = R.layout.fund_list_activity

    override fun providerVMClass(): Class<FundListViewModel>? = FundListViewModel::class.java
    private val TAG = this.javaClass.simpleName
    var createAdapter: FundListAdapter? = null

    //    val tempList:MutableList<FundInfo> = mutableListOf()
    val timer = Timer()
    override fun initView() {
        recycleView.layoutManager = LinearLayoutManager(this)
        createAdapter = createAdapter()

        // 拖拽监听
        val listener: OnItemDragListener = object : OnItemDragListener {
            override fun onItemDragStart(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                LogUtils.i("drag start")
                val holder = viewHolder as BaseViewHolder

                // 开始时，item背景色变化，demo这里使用了一个动画渐变，使得自然
                val startColor: Int = Color.WHITE
                val endColor: Int = Color.rgb(245, 245, 245)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val v = ValueAnimator.ofArgb(startColor, endColor)
                    v.addUpdateListener { animation -> holder.itemView.setBackgroundColor(animation.animatedValue as Int) }
                    v.duration = 300
                    v.start()
                }
            }

            override fun onItemDragMoving(source: RecyclerView.ViewHolder, from: Int, target: RecyclerView.ViewHolder, to: Int) {
                LogUtils.i("move from: " + source.adapterPosition + " to: " + target.adapterPosition)
            }

            override fun onItemDragEnd(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                LogUtils.i("drag end")
                val holder = viewHolder as BaseViewHolder
                // 结束时，item背景色变化，demo这里使用了一个动画渐变，使得自然
                val startColor: Int = Color.rgb(245, 245, 245)
                val endColor: Int = Color.WHITE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val v = ValueAnimator.ofArgb(startColor, endColor)
                    v.addUpdateListener { animation -> holder.itemView.setBackgroundColor(animation.animatedValue as Int) }
                    v.duration = 300
                    v.start()
                }
                viewModel.sortData(createAdapter!!.data)
            }
        }


        createAdapter?.apply {
//            addDraggableModule(this)
            draggableModule.isDragEnabled = true
            draggableModule.setOnItemDragListener(listener)
            draggableModule.itemTouchHelperCallback.setDragMoveFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN)
        }

        recycleView.addItemDecoration(DividerItemDecoration(this, LinearLayout.VERTICAL))
        recycleView.adapter = createAdapter
    }

    override fun initListener() {
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = true
            viewModel.refreshData()
        }
        createAdapter?.setOnItemClickListener { adapter, view, position ->
            val builder = AlertDialog.Builder(FundListActivity@ this)
            val temp = createAdapter!!.data.get(position)
            builder.setTitle("删除基金").setMessage("确定删除该条目?")
                    .setNegativeButton("取消", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            dialog.dismiss()

                        }
                    }).setPositiveButton("确定", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            viewModel.deleteFund(temp.fundcode)
                            createAdapter?.remove(temp)
                        }
                    })
            val create = builder.create()
            create.show()
            false

        }
        tv_add.setOnClickListener {
            val editText = EditText(FundListActivity@ this)
            editText.filters = arrayOf<InputFilter>(LengthFilter(6)) //最大输入长度
            editText.inputType = EditorInfo.TYPE_CLASS_NUMBER
            editText.hint = "请输入基金代码(6位数字)"
            val builder = AlertDialog.Builder(FundListActivity@ this)
            builder.setTitle("添加基金").setView(editText)
                    .setNegativeButton("取消", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            dialog.dismiss()

                        }
                    }).setPositiveButton("确定", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            val text: String = editText.text.toString()
                            viewModel.saveFundCode(text)

                        }
                    })
            builder.show()

        }
    }


    override fun initData() {
        viewModel.getError().observe(this, Observer<Throwable> { e ->
            LogUtils.e("发生异常", e)
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        })
        viewModel.getFinally().observe(this, Observer<Int> {
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        })

        if (viewModel.dataSourceType == 0){
            val listFundInfos = DaoRepository.listFundInfos()
            createAdapter?.setNewInstance(listFundInfos)
        }

        //获取最新数据刷新列表
        viewModel.getArticle().observe(this, Observer<FundInfo> {
            it?.run {
                /* if (swipeRefreshLayout.isRefreshing){
                     swipeRefreshLayout.isRefreshing = false
                 }*/
                synchronized(TAG) {
                    val temp = it
                    if (createAdapter!!.data.size == 0) {
                        createAdapter?.addData(temp)
                    } else {
                        var exist = false
                        for ((index, element) in createAdapter!!.data.withIndex()) {
                            if (element.fundcode == temp.fundcode) {
                                createAdapter?.setData(index, temp)
                                exist = true
                                break //注意跳出
                            } else {
                                exist = false
                            }
                        }
                        if (exist) {
                            return@Observer
                        }
                        createAdapter?.addData(temp)
                    }
                }
            }
        })
        val df = SimpleDateFormat("HH:mm:ss") //设置日期格式


        var nowTime: Date?
        var beginTime: Date?
        var endTime: Date?
        var flag = false
        try {
            nowTime = df.parse(df.format(Date()))
            //开盘时间区
            beginTime = df.parse("09:30:00")
            endTime = df.parse("15:00:00")
            flag = DateUtils.belongCalendar(nowTime, beginTime, endTime);
        } catch (e: Exception) {
        }
        val timerTask = object : TimerTask() {
            override fun run() {
              runOnUiThread {
                  viewModel.refreshData()
              }
            }
        }
        if (flag) {
            timer.scheduleAtFixedRate(timerTask, 10 * 1000, 10 * 1000)
        }

    }


    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }

    private fun createAdapter(): FundListAdapter {
        return FundListAdapter(null)
    }
}
