package com.sjl.fund.mvi

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.listener.OnItemDragListener
import com.chad.library.adapter.base.listener.OnLoadMoreListener
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.R
import com.sjl.fund.adapter.FundListAdapter
import com.sjl.fund.db.DaoRepository
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.mvvm.FundDetailActivity
import com.sjl.fund.util.DateUtils
import com.sjl.fund.util.MoneyUtils
import com.sjl.fund.util.ExcelImportHelper
import kotlinx.android.synthetic.main.fund_list_activity.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 主页 - 基金列表
 * @author Kelly
 * @version 2.0.0
 */
class FundListActivity2 : BaseViewModelActivity<FundListViewModel2>() {

    override fun getLayoutId(): Int = R.layout.fund_list_activity

    override fun providerVMClass(): Class<FundListViewModel2>? = FundListViewModel2::class.java

    private val TAG = this.javaClass.simpleName
    private var createAdapter: FundListAdapter? = null
    private val timer = Timer()
    private var searchDialog: AlertDialog? = null
    private var searchAdapter: FundListAdapter? = null
    
    // 分页相关
    private var currentPage = 0
    private val pageSize = 20
    private var allFundList: List<FundInfo> = emptyList()

    private val REQUEST_CODE_IMPORT = 1001

    override fun initView() {
        recycleView.layoutManager = LinearLayoutManager(this)
        createAdapter = FundListAdapter(null)

        // 拖拽监听
        val listener = object : OnItemDragListener {
            override fun onItemDragStart(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                val holder = viewHolder as BaseViewHolder
                val startColor = Color.WHITE
                val endColor = Color.rgb(245, 245, 245)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val v = ValueAnimator.ofArgb(startColor, endColor)
                    v.addUpdateListener { animation -> holder.itemView.setBackgroundColor(animation.animatedValue as Int) }
                    v.duration = 300
                    v.start()
                }
            }

            override fun onItemDragMoving(source: RecyclerView.ViewHolder, from: Int, target: RecyclerView.ViewHolder, to: Int) {}

            override fun onItemDragEnd(viewHolder: RecyclerView.ViewHolder, pos: Int) {
                val holder = viewHolder as BaseViewHolder
                val startColor = Color.rgb(245, 245, 245)
                val endColor = Color.WHITE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val v = ValueAnimator.ofArgb(startColor, endColor)
                    v.addUpdateListener { animation -> holder.itemView.setBackgroundColor(animation.animatedValue as Int) }
                    v.duration = 300
                    v.start()
                }
                viewModel.dispatch(FundListIntent.SortData(createAdapter!!.data))
            }
        }

        createAdapter?.apply {
            draggableModule.isDragEnabled = true
            draggableModule.setOnItemDragListener(listener)
            
            // 启用加载更多
            loadMoreModule.isEnableLoadMore = true

            loadMoreModule.setOnLoadMoreListener {
                loadMoreData()
            }
        }

        recycleView.adapter = createAdapter

        // 设置侧滑删除
        setupSwipeToDelete()
    }

    /**
     * 设置侧滑删除功能
     */
    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, // 不支持拖拽，让draggableModule处理
            ItemTouchHelper.LEFT // 只支持向左滑动删除
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                // 不支持拖拽，返回false
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val fundInfo = createAdapter?.data?.get(position)
                    if (fundInfo != null) {
                        // 显示确认对话框
                        AlertDialog.Builder(this@FundListActivity2)
                            .setTitle("删除")
                            .setMessage("确定删除该条目(${fundInfo.fundcode})?")
                            .setNegativeButton("取消") { dialog, _ ->
                                dialog.dismiss()
                                // 恢复被滑动的item
                                createAdapter?.notifyItemChanged(position)
                            }
                            .setPositiveButton("确定") { dialog, _ ->
                                viewModel.dispatch(FundListIntent.DeleteFund(fundInfo.fundcode))
                                createAdapter?.removeAt(position)
                            }
                            .show()
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val itemHeight = itemView.bottom - itemView.top

                    // 绘制红色背景
                    val background = Paint().apply {
                        color = Color.parseColor("#FF4444")
                    }
                    c.drawRect(
                        itemView.right.toFloat() + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        background
                    )

                    // 绘制删除文字
                    val deleteText = "删除"
                    val textPaint = Paint().apply {
                        color = Color.WHITE
                        textSize = 48f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    val textX = itemView.right.toFloat() + dX / 2
                    val textY = itemView.top.toFloat() + itemHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2
                    c.drawText(deleteText, textX, textY, textPaint)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(recycleView)
    }

    override fun initListener() {
        // 下拉刷新
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = true
            viewModel.dispatch(FundListIntent.RefreshData)
        }

        // 列表点击进入详情
        createAdapter?.apply {
            setOnItemClickListener { adapter, view, position ->
                val temp = createAdapter!!.data[position]
                val intent = Intent(this@FundListActivity2, FundDetailActivity::class.java)
                intent.putExtra(FundDetailActivity.EXTRA_FUND_CODE, temp.fundcode)
                intent.putExtra(FundDetailActivity.EXTRA_FUND_NAME, temp.name)
                startActivity(intent)
            }

            // 点击名称编辑
            setOnItemChildClickListener { adapter, view, position ->
                val temp = createAdapter!!.data[position]
                if (view.id == R.id.tv_name) {
                    showEditDialog(temp)
                }
            }
        }

        // 添加按钮 - 显示二级菜单
        tv_add.setOnClickListener {
            showPopupMenu(it)
        }

        // 搜索按钮
        iv_search.setOnClickListener {
            showSearchDialog()
        }
    }
    override fun initData() {


    }
    /** 显示菜单弹出框 */
    private fun showPopupMenu(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        // 单个添加
        popupView.findViewById<TextView>(R.id.tv_single_add).setOnClickListener {
            popupWindow.dismiss()
            showAddDialog()
        }

        // 批量导入
        popupView.findViewById<TextView>(R.id.tv_batch_import).setOnClickListener {
            popupWindow.dismiss()
            openFilePicker()
        }

        // 导出模板
        popupView.findViewById<TextView>(R.id.tv_export_template).setOnClickListener {
            popupWindow.dismiss()
            exportTemplate()
        }

        popupWindow.setBackgroundDrawable(getDrawable(R.drawable.bg_popup_menu))
        popupWindow.elevation = 8f
        popupWindow.showAsDropDown(anchor, 0, 0)
    }

    /** 打开文件选择器 */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT)
        } catch (e: Exception) {
            // 如果没有xlsx选择器，使用通用的
            val intent2 = Intent(Intent.ACTION_GET_CONTENT)
            intent2.type = "*/*"
            intent2.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent2, REQUEST_CODE_IMPORT)
        }
    }

    /** 导出模板文件 */
    private fun exportTemplate() {
        lifecycleScope.launch {
            try {
                val uri = ExcelImportHelper.exportTemplate(this@FundListActivity2)
                
                // 分享文件
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "分享模板到"))
                
            } catch (e: Exception) {
                LogUtils.e("导出模板失败", e)
                AlertDialog.Builder(this@FundListActivity2)
                    .setTitle("导出失败")
                    .setMessage(e.message)
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    /** 处理文件选择结果 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (requestCode == REQUEST_CODE_IMPORT && uri != null) {
                importFromExcel(uri)
            }
        }
    }

    /** 从Excel导入基金 */
    private fun importFromExcel(uri: Uri) {
        lifecycleScope.launch {
            try {
                val fundCodes = ExcelImportHelper.importFromExcel(this@FundListActivity2, uri)
                if (fundCodes.isEmpty()) {
                    AlertDialog.Builder(this@FundListActivity2)
                        .setTitle("导入失败")
                        .setMessage("未找到有效的基金代码")
                        .setPositiveButton("确定", null)
                        .show()
                    return@launch
                }

                // 批量添加基金
                var successCount = 0
                for (code in fundCodes) {
                    viewModel.dispatch(FundListIntent.SaveFundCode(code, 0, 0.0))
                    successCount++
                }

                AlertDialog.Builder(this@FundListActivity2)
                    .setTitle("导入成功")
                    .setMessage("成功导入 $successCount 只基金\n请下拉刷新获取数据")
                    .setPositiveButton("确定", null)
                    .show()

            } catch (e: Exception) {
                LogUtils.e("导入Excel失败", e)
                AlertDialog.Builder(this@FundListActivity2)
                    .setTitle("导入失败")
                    .setMessage("解析文件失败：${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    /** 显示搜索对话框 */
    private fun showSearchDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_search, null)
        val etSearch = view.findViewById<EditText>(R.id.et_search)
        val rvResult = view.findViewById<RecyclerView>(R.id.rv_search_result)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)

        searchAdapter = FundListAdapter(null)
        rvResult.layoutManager = LinearLayoutManager(this)
        rvResult.adapter = searchAdapter

        searchAdapter?.setOnItemClickListener { adapter, v, position ->
            val temp = searchAdapter!!.data[position]
            val intent = Intent(this@FundListActivity2, FundDetailActivity::class.java)
            intent.putExtra(FundDetailActivity.EXTRA_FUND_CODE, temp.fundcode)
            intent.putExtra(FundDetailActivity.EXTRA_FUND_NAME, temp.name)
            startActivity(intent)
            searchDialog?.dismiss()
        }

        // 搜索监听
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString()?.trim() ?: ""
                if (keyword.isEmpty()) {
                    rvResult.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "输入关键词搜索已添加的基金"
                } else {
                    searchLocal(keyword, rvResult, tvEmpty)
                }
            }
        })

        searchDialog = AlertDialog.Builder(this)
            .setTitle("搜索基金")
            .setView(view)
            .setNegativeButton("取消", null)
            .create()
        searchDialog?.show()
    }

    /** 搜索本地数据库 */
    private fun searchLocal(keyword: String, rvResult: RecyclerView, tvEmpty: TextView) {
        lifecycleScope.launch {
            val allFunds = DaoRepository.listFundInfos() ?: emptyList()
            val filtered = allFunds.filter { fund ->
                fund.fundcode.contains(keyword) || fund.name.contains(keyword)
            }

            if (filtered.isEmpty()) {
                rvResult.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "未找到匹配的基金"
            } else {
                rvResult.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE
                searchAdapter?.setNewInstance(filtered.toMutableList())
            }
        }
    }

    /** 显示添加对话框 */
    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.fund_add_item, null)
        val etFundCode = view.findViewById<EditText>(R.id.et_fund_code)
        val cbHold = view.findViewById<CheckBox>(R.id.cb_hold)
        val etFundMoney = view.findViewById<EditText>(R.id.et_fund_money)

        cbHold.setOnCheckedChangeListener { _, isChecked ->
            etFundMoney.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("添加基金")
            .setView(view)
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("确定") { dialog, _ ->
                val fundCode = etFundCode.text.toString().trim()
                val isChecked = cbHold.isChecked
                val fundMoney = etFundMoney.text.toString().trim()
                val holdFlag = if (isChecked) 1 else 0
                val holdMoney = if (fundMoney.isNotEmpty()) fundMoney.toDouble() else 0.0
                viewModel.dispatch(FundListIntent.SaveFundCode(fundCode, holdFlag, holdMoney))
            }
            .show()
    }

    /** 显示编辑对话框 */
    private fun showEditDialog(fundInfo: FundInfo) {
        val view = layoutInflater.inflate(R.layout.fund_add_item, null)
        val etFundCode = view.findViewById<EditText>(R.id.et_fund_code)
        val cbHold = view.findViewById<CheckBox>(R.id.cb_hold)
        val etFundMoney = view.findViewById<EditText>(R.id.et_fund_money)

        etFundCode.isEnabled = false
        etFundCode.setText(fundInfo.fundcode)
        etFundMoney.setText(MoneyUtils.formatMoney(fundInfo.holdMoney, 2))
        cbHold.isChecked = fundInfo.holdFlag == 1
        etFundMoney.visibility = if (cbHold.isChecked) View.VISIBLE else View.GONE

        cbHold.setOnCheckedChangeListener { _, isChecked ->
            etFundMoney.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("修改")
            .setView(view)
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("确定") { dialog, _ ->
                val isChecked = cbHold.isChecked
                val fundMoney = etFundMoney.text.toString().trim()
                fundInfo.holdFlag = if (isChecked) 1 else 0
                fundInfo.holdMoney = if (isChecked && fundMoney.isNotEmpty()) fundMoney.toDouble() else 0.0
                viewModel.dispatch(FundListIntent.Update(fundInfo))
            }
            .show()
    }

    /** 加载更多数据 */
    private fun loadMoreData() {
        val startIndex = (currentPage + 1) * pageSize
        if (startIndex >= allFundList.size) {
            // 没有更多数据
            createAdapter?.loadMoreModule?.loadMoreEnd()
            return
        }
        
        val endIndex = minOf(startIndex + pageSize, allFundList.size)
        val pageData = allFundList.subList(startIndex, endIndex)
        
        currentPage++
        createAdapter?.addData(pageData)
        createAdapter?.loadMoreModule?.loadMoreComplete()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }

    override fun startObserve() {
        lifecycleScope.launch {
            viewModel.viewState.collect { state ->
                when (state) {
                    is FundListUiState.InitSuccess -> {
                        allFundList = state.resData ?: emptyList()
                        currentPage = 0
                        // 加载第一页
                        val firstPage = allFundList.take(pageSize)
                        createAdapter?.setNewInstance(firstPage.toMutableList())
                    }
                    is FundListUiState.LoadSuccess -> {
                        // 单条数据更新或添加
                        state.resData?.let { data ->
                            synchronized(TAG) {
                                val existingIndex = createAdapter?.data?.indexOfFirst { it.fundcode == data.fundcode } ?: -1
                                if (existingIndex >= 0) {
                                    createAdapter?.setData(existingIndex, data)
                                } else {
                                    createAdapter?.addData(data)
                                }
                            }
                        }
                    }
                    is FundListUiState.LoadError -> {
                        LogUtils.e("发生异常", state.error)
                        if (swipeRefreshLayout.isRefreshing) {
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                    is FundListUiState.LoadFinish -> {
                        if (swipeRefreshLayout.isRefreshing) {
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }

        viewModel.dispatch(FundListIntent.RefreshData)

        // 定时刷新
        val df = SimpleDateFormat("HH:mm:ss")
        val nowTime = df.parse(df.format(Date()))
        val beginTime = df.parse("09:30:00")
        val endTime = df.parse("15:00:00")
        val flag = DateUtils.belongCalendar(nowTime, beginTime, endTime)

        if (flag) {
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        viewModel.dispatch(FundListIntent.RefreshData)
                    }
                }
            }, 10000, 10000)
        }
    }
}