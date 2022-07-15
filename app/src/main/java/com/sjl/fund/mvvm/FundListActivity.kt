package com.sjl.fund.mvvm


import android.animation.ValueAnimator
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.CheckBox
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
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.R
import com.sjl.fund.adapter.FundListAdapter
import com.sjl.fund.db.DaoRepository
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.util.BaseClickListener
import com.sjl.fund.util.DateUtils
import com.sjl.fund.util.MoneyUtils
import kotlinx.android.synthetic.main.fund_list_activity.*
import java.text.SimpleDateFormat
import java.util.*


class FundListActivity : BaseViewModelActivity<FundListViewModel>() {

    override fun getLayoutId(): Int = R.layout.fund_list_activity

    override fun providerVMClass(): Class<FundListViewModel>? = FundListViewModel::class.java
    private val TAG = this.javaClass.simpleName
    var createAdapter: FundListAdapter? = null

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


        createAdapter?.apply {
            setOnItemClickListener { adapter, view, position ->
//                val builder = AlertDialog.Builder(FundListActivity@ this)
                val builder = AlertDialog.Builder(this@FundListActivity)
                val temp = createAdapter!!.data.get(position)
                builder.setTitle("删除").setMessage("确定删除该条目("+temp.fundcode+")?")
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
            }

          setOnItemChildClickListener() {
                adapter, view, position ->
              val temp = createAdapter!!.data.get(position)
              when(view.id) {
                  R.id.tv_name -> {
                      val view = layoutInflater.inflate(R.layout.fund_add_item,null)
                      val et_fund_code = view.findViewById<EditText>(R.id.et_fund_code)
                      et_fund_code.isEnabled = false
                      et_fund_code.setText(temp.fundcode)
                      val cb_hold = view.findViewById<CheckBox>(R.id.cb_hold)
                      val et_fund_money = view.findViewById<EditText>(R.id.et_fund_money)
                      et_fund_money.setText(MoneyUtils.formatMoney(temp.holdMoney,2))
                      cb_hold.isChecked = temp.holdFlag == 1
                      if(cb_hold.isChecked){
                          et_fund_money.visibility = View.VISIBLE
                      }else{
                          et_fund_money.visibility = View.GONE
                      }
                      cb_hold.setOnCheckedChangeListener { buttonView, isChecked ->
                          if (isChecked){
                              et_fund_money.visibility = View.VISIBLE
                          }else{
                              et_fund_money.visibility = View.GONE
                          }
                      }
                      val builder = AlertDialog.Builder(this@FundListActivity)
                      builder.setTitle("修改").setView(view)
                              .setNegativeButton("取消", object : DialogInterface.OnClickListener {
                                  override fun onClick(dialog: DialogInterface, which: Int) {
                                      dialog.dismiss()

                                  }
                              }).setPositiveButton("确定", object : DialogInterface.OnClickListener {
                                  override fun onClick(dialog: DialogInterface, which: Int) {
                                      val isChecked: Boolean = cb_hold.isChecked
                                      val fundMoney: String = et_fund_money.text.toString().trim()
                                      val holdFlag = if (isChecked) 1 else 0
                                      val  holdMoney = if (isChecked && !fundMoney.isBlank()) fundMoney.toDouble() else 0.0
                                      temp.holdFlag = holdFlag
                                      temp.holdMoney = holdMoney
                                      viewModel.update(temp)

                                  }
                              })
                      builder.show()
                  }
                  else -> {

                  }
              }

            }
        }
        tv_add.setOnClickListener {
         /*   val et_fund_code = EditText(FundListActivity@ this)
            et_fund_code.filters = arrayOf<InputFilter>(LengthFilter(6)) //最大输入长度
            et_fund_code.inputType = EditorInfo.TYPE_CLASS_NUMBER
            et_fund_code.hint = "请输入基金代码(6位数字)"*/
            val view = layoutInflater.inflate(R.layout.fund_add_item,null)
            val et_fund_code = view.findViewById<EditText>(R.id.et_fund_code)
            val cb_hold = view.findViewById<CheckBox>(R.id.cb_hold)
            val et_fund_money = view.findViewById<EditText>(R.id.et_fund_money)

            cb_hold.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked){
                    et_fund_money.visibility = View.VISIBLE
                }else{
                    et_fund_money.visibility = View.GONE
                }
            }

            val builder = AlertDialog.Builder(FundListActivity@ this)
            builder.setTitle("添加基金").setView(view)
                    .setNegativeButton("取消", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            dialog.dismiss()

                        }
                    }).setPositiveButton("确定", object : DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface, which: Int) {
                            val fundCode: String = et_fund_code.text.toString().trim()
                            val isChecked: Boolean = cb_hold.isChecked
                            val fundMoney: String = et_fund_money.text.toString().trim()
                            val holdFlag = if (isChecked) 1 else 0
                            val  holdMoney = if (fundMoney.isNotEmpty()) fundMoney.toDouble() else 0.0
                            viewModel.saveFundCode(fundCode,holdFlag,holdMoney)

                        }
                    })
            builder.show()

        }
    }


    override fun initData() {


    }


    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }

    private fun createAdapter(): FundListAdapter {
        return FundListAdapter(null)
    }

    override fun startObserve() {
        viewModel.errorGlobal.observe(this, Observer<Throwable> { e ->
            LogUtils.e("发生异常", e)
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        })
        viewModel.finallyGlobal.observe(this, Observer<Int> {
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        })

        //初始化
        viewModel.listFundInfos.observe(this, Observer<List<FundInfo>> {
            it?.run {
                synchronized(TAG) {
                    createAdapter?.addData(it)
                }
            }
        })
        //更新
        viewModel.updateFundInfo.observe(this, Observer<FundInfo> {
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
}
