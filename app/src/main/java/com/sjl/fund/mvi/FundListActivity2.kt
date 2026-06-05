package com.sjl.fund.mvi

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.chad.library.adapter.base.listener.OnItemDragListener
import com.chad.library.adapter.base.listener.OnLoadMoreListener
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.tabs.TabLayout
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.R
import com.sjl.fund.adapter.FundListAdapter
import com.sjl.fund.adapter.IndexListAdapter
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
 * 主页 - 基金列表（双Tab + 指数行情）
 * @author Kelly
 * @version 3.0.0
 */
class FundListActivity2 : BaseViewModelActivity<FundListViewModel2>() {

    override fun getLayoutId(): Int = R.layout.fund_list_activity

    override fun providerVMClass(): Class<FundListViewModel2>? = FundListViewModel2::class.java

    private val TAG = this.javaClass.simpleName
    private var createAdapter: FundListAdapter? = null
    private val timer = Timer()
    private var searchDialog: Dialog? = null
    private var searchAdapter: FundListAdapter? = null
    private var indexAdapter: IndexListAdapter? = null

    // 分页相关
    private var currentPage = 0
    private val pageSize = 20
    private var allFundList: List<FundInfo> = emptyList()

    private val REQUEST_CODE_IMPORT = 1001

    // 当前Tab: 0=自选, 1=其他基金
    private var currentTabIndex = 0

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

        // 初始化指数水平滚动列表
        initIndexRecyclerView()

        // 初始化TabLayout
        initTabLayout()
    }

    /**
     * 初始化指数水平滚动列表
     */
    private fun initIndexRecyclerView() {
        rv_index.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        indexAdapter = IndexListAdapter()
        rv_index.adapter = indexAdapter
    }

    /**
     * 初始化TabLayout
     */
    private fun initTabLayout() {
        tl_fund_type.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabIndex = tab?.position ?: 0
                viewModel.dispatch(FundListIntent.SwitchTab(currentTabIndex))
                viewModel.dispatch(FundListIntent.RefreshData)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
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
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val fundInfo = createAdapter?.data?.get(position)
                    if (fundInfo != null) {
                        showDeleteConfirmDialog(fundInfo, position)
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

    /**
     * 显示删除确认对话框（优化版）
     */
    private fun showDeleteConfirmDialog(fundInfo: FundInfo, position: Int) {
        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 24)
            setBackgroundResource(R.drawable.bg_dialog_rounded)
        }

        val titleText = TextView(this).apply {
            text = "删除确认"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentView.addView(titleText)

        val msgText = TextView(this).apply {
            text = "确定删除 ${fundInfo.name}(${fundInfo.fundcode})?"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 20, 0, 0)
        }
        contentView.addView(msgText)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 32, 0, 0)
        }

        val dialog = Dialog(this).apply {
            setContentView(contentView)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCanceledOnTouchOutside(false)
            setCancelable(false)
        }

        val cancelBtn = TextView(this).apply {
            text = "取消"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                dialog.dismiss()
                createAdapter?.notifyItemChanged(position)
            }
        }

        val confirmBtn = TextView(this).apply {
            text = "删除"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1677FF"))
            setPadding(40, 16, 40, 16)
            setOnClickListener {
                viewModel.dispatch(FundListIntent.DeleteFund(fundInfo.fundcode))
                createAdapter?.removeAt(position)
                dialog.dismiss()
            }
        }

        btnLayout.addView(cancelBtn)
        btnLayout.addView(confirmBtn)
        contentView.addView(btnLayout)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun initListener() {
        // 下拉刷新
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = true
            viewModel.dispatch(FundListIntent.RefreshData)
            viewModel.dispatch(FundListIntent.LoadIndexData)
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

        // 添加按钮
        tv_add.setOnClickListener {
            showPopupMenu(it)
        }

        // 搜索按钮
        iv_search.setOnClickListener {
            showSearchDialog()
        }
    }

    override fun initData() {
        // 初始化时加载指数数据
        viewModel.dispatch(FundListIntent.LoadIndexData)
    }

    /** 显示菜单弹出框 */
    private fun showPopupMenu(anchor: View) {
        val popupView = layoutInflater.inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        popupView.findViewById<TextView>(R.id.tv_single_add).setOnClickListener {
            popupWindow.dismiss()
            showAddDialog()
        }

        popupView.findViewById<TextView>(R.id.tv_batch_import).setOnClickListener {
            popupWindow.dismiss()
            openFilePicker()
        }

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

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, "分享模板到"))

            } catch (e: Exception) {
                LogUtils.e("导出模板失败", e)
                showAlertDialog("导出失败", e.message ?: "未知错误")
            }
        }
    }

    /** 处理文件选择结果 */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (requestCode == REQUEST_CODE_IMPORT && uri != null) {
                showImportCategoryDialog(uri)
            }
        }
    }

    /** 显示导入分类选择对话框 */
    private fun showImportCategoryDialog(uri: Uri) {
        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 24)
            setBackgroundResource(R.drawable.bg_dialog_rounded)
        }

        val titleText = TextView(this).apply {
            text = "\u5bfc\u5165\u5230"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentView.addView(titleText)

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val rbSelf = RadioButton(this).apply {
            id = View.generateViewId()
            text = "\u81ea\u9009"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setButtonDrawable(resources.getDrawable(R.drawable.selector_radio, theme))
            setPadding(40, 12, 0, 12)
            isChecked = true
        }
        radioGroup.addView(rbSelf)

        val rbOther = RadioButton(this).apply {
            id = View.generateViewId()
            text = "\u5176\u4ed6\u57fa\u91d1"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setButtonDrawable(resources.getDrawable(R.drawable.selector_radio, theme))
            setPadding(40, 12, 0, 12)
        }
        radioGroup.addView(rbOther)

        contentView.addView(radioGroup)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 24, 0, 0)
        }

        val cancelBtn = TextView(this).apply {
            text = "\u53d6\u6d88"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            setPadding(32, 16, 32, 16)
        }

        val confirmBtn = TextView(this).apply {
            text = "\u5bfc\u5165"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1677FF"))
            setPadding(40, 16, 40, 16)
        }

        val dialog = Dialog(this).apply {
            setContentView(contentView)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        confirmBtn.setOnClickListener {
            val fundType = if (radioGroup.indexOfChild(
                    radioGroup.findViewById(radioGroup.checkedRadioButtonId)
                ) == 0) 0 else 1
            dialog.dismiss()
            importFromExcel(uri, fundType)
        }

        btnLayout.addView(cancelBtn)
        btnLayout.addView(confirmBtn)
        contentView.addView(btnLayout)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /** 从Excel导入基金 */
    private fun importFromExcel(uri: Uri, fundType: Int = 0) {
        lifecycleScope.launch {
            try {
                val fundCodes = ExcelImportHelper.importFromExcel(this@FundListActivity2, uri)
                if (fundCodes.isEmpty()) {
                    showAlertDialog("导入失败", "未找到有效的基金代码")
                    return@launch
                }

                var successCount = 0
                for (code in fundCodes) {
                    viewModel.dispatch(FundListIntent.SaveFundCode(code, 0, 0.0, fundType,1))
                    successCount++
                }

                showAlertDialog("导入成功", "成功导入 $successCount 只基金\n请下拉刷新获取数据")

            } catch (e: Exception) {
                LogUtils.e("导入Excel失败", e)
                showAlertDialog("导入失败", "解析文件失败：${e.message}")
            }
        }
    }

    /** 显示搜索对话框 */
    private fun showSearchDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_search, null)
        val etSearch = view.findViewById<EditText>(R.id.et_search)
        val rvResult = view.findViewById<RecyclerView>(R.id.rv_search_result)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty)
        val tvCancel = view.findViewById<TextView>(R.id.tv_search_cancel)

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

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString()?.trim() ?: ""
                if (keyword.isEmpty()) {
                    rvResult.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "\u8f93\u5165\u5173\u952e\u8bcd\u641c\u7d22\u5df2\u6dfb\u52a0\u7684\u57fa\u91d1"
                } else {
                    searchLocal(keyword, rvResult, tvEmpty)
                }
            }
        })

        tvCancel.setOnClickListener { searchDialog?.dismiss() }

        val searchContentView = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_dialog_rounded)
            addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        searchDialog = Dialog(this).apply {
            setContentView(searchContentView)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            show()
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
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

    /** 显示添加对话框（自定义美化版） */
    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_fund, null)
        // 包裹白色圆角背景
        val wrapper = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_dialog_rounded)
            addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val etFundCode = wrapper.findViewById<EditText>(R.id.et_fund_code)
        val cbHold = wrapper.findViewById<CheckBox>(R.id.cb_hold)
        val etFundMoney = wrapper.findViewById<EditText>(R.id.et_fund_money)
        val rgFundType = wrapper.findViewById<RadioGroup>(R.id.rg_fund_type)
        val tvCancel = wrapper.findViewById<TextView>(R.id.tv_cancel)
        val tvConfirm = wrapper.findViewById<TextView>(R.id.tv_confirm)

        // 设置初始Tab
        if (currentTabIndex == 0) {
            wrapper.findViewById<RadioButton>(R.id.rb_self_select).isChecked = true
        } else {
            wrapper.findViewById<RadioButton>(R.id.rb_other).isChecked = true
        }

        cbHold.setOnCheckedChangeListener { _, isChecked ->
            etFundMoney.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val dialog = Dialog(this).apply {
            setContentView(wrapper)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        tvCancel.setOnClickListener { dialog.dismiss() }
        tvConfirm.setOnClickListener {
            val fundCode = etFundCode.text.toString().trim()
            if (fundCode.isEmpty()) {
                etFundCode.error = "请输入基金代码"
                return@setOnClickListener
            }
            val isChecked = cbHold.isChecked
            val fundMoney = etFundMoney.text.toString().trim()
            val holdFlag = if (isChecked) 1 else 0
            val holdMoney = if (fundMoney.isNotEmpty()) fundMoney.toDouble() else 0.0
            val fundType = if (rgFundType.checkedRadioButtonId == R.id.rb_self_select) 0 else 1
            viewModel.dispatch(FundListIntent.SaveFundCode(fundCode, holdFlag, holdMoney, fundType,1))
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /** 显示编辑对话框（自定义美化版） */
    private fun showEditDialog(fundInfo: FundInfo) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_fund, null)
        // 包裹白色圆角背景
        val wrapper = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_dialog_rounded)
            addView(view, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val cbHold = wrapper.findViewById<CheckBox>(R.id.cb_hold)
        val etFundMoney = wrapper.findViewById<EditText>(R.id.et_fund_money)
        val tvFundName = wrapper.findViewById<TextView>(R.id.tv_fund_name)
        val tvCancel = wrapper.findViewById<TextView>(R.id.tv_cancel)
        val tvConfirm = wrapper.findViewById<TextView>(R.id.tv_confirm)
        val rgEditType = wrapper.findViewById<RadioGroup>(R.id.rg_edit_type)

        tvFundName.text = "${fundInfo.name} (${fundInfo.fundcode})"
        etFundMoney.setText(MoneyUtils.formatMoney(fundInfo.holdMoney, 2))
        cbHold.isChecked = fundInfo.holdFlag == 1
        etFundMoney.visibility = if (cbHold.isChecked) View.VISIBLE else View.GONE

        // 设置当前所属分类
        if (fundInfo.fundType == 0) {
            wrapper.findViewById<RadioButton>(R.id.rb_edit_self).isChecked = true
        } else {
            wrapper.findViewById<RadioButton>(R.id.rb_edit_other).isChecked = true
        }

        cbHold.setOnCheckedChangeListener { _, isChecked ->
            etFundMoney.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val dialog = Dialog(this).apply {
            setContentView(wrapper)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        tvCancel.setOnClickListener { dialog.dismiss() }
        tvConfirm.setOnClickListener {
            val isChecked = cbHold.isChecked
            val fundMoney = etFundMoney.text.toString().trim()
            fundInfo.holdFlag = if (isChecked) 1 else 0
            fundInfo.holdMoney = if (isChecked && fundMoney.isNotEmpty()) fundMoney.toDouble() else 0.0
            // 更新所属分类
            val newFundType = if (rgEditType.checkedRadioButtonId == R.id.rb_edit_self) 0 else 1
            val oldFundType = fundInfo.fundType
            fundInfo.fundType = newFundType
            viewModel.dispatch(FundListIntent.Update(fundInfo))
            // 分类变更立即从当前列表移除
            if (oldFundType != newFundType) {
                val idx = createAdapter?.data?.indexOfFirst { it.fundcode == fundInfo.fundcode } ?: -1
                if (idx >= 0) {
                    createAdapter?.removeAt(idx)
                }
            }
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /** 通用提示对话框 */
    private fun showAlertDialog(title: String, message: String) {
        val contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 24)
            setBackgroundResource(R.drawable.bg_dialog_rounded)
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentView.addView(titleText)

        val msgText = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 16, 0, 0)
        }
        contentView.addView(msgText)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, 28, 0, 0)
        }

        val dialog = Dialog(this).apply {
            setContentView(contentView)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        val okBtn = TextView(this).apply {
            text = "\u786e\u5b9a"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1677FF"))
            setPadding(40, 16, 40, 16)
            setOnClickListener { dialog.dismiss() }
        }
        btnLayout.addView(okBtn)
        contentView.addView(btnLayout)

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /** 加载更多数据 */
    private fun loadMoreData() {
        val startIndex = (currentPage + 1) * pageSize
        if (startIndex >= allFundList.size) {
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
                        val firstPage = allFundList.take(pageSize)
                        createAdapter?.setNewInstance(firstPage.toMutableList())
                    }
                    is FundListUiState.LoadSuccess -> {
                        // 只处理当前Tab的数据
                        if (state.fundType != currentTabIndex) return@collect
                        state.resData?.let { data ->
                            synchronized(TAG) {
                                val existingIndex = createAdapter?.data?.indexOfFirst { it.fundcode == data.fundcode } ?: -1
                                LogUtils.i("update index: $existingIndex")
                                if (existingIndex >= 0) {
                                    createAdapter?.setData(existingIndex, data)
                                } else {
                                    if (data.operateType == 1){
                                        //1为新增基金
                                        createAdapter?.addData(0,data)
                                    }else{
                                        // 新基金插入到列表首位
                                        createAdapter?.addData(data)
                                    }


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
                        // 网络加载完成后，同步allFundList与Adapter数据，确保分页排序一致
                        createAdapter?.data?.let { adapterData ->
                            if (adapterData.isNotEmpty()) {
                                allFundList = adapterData.toList()
                                createAdapter?.loadMoreModule?.loadMoreComplete()
                            }
                        }
                    }
                    is FundListUiState.IndexDataLoaded -> {
                        indexAdapter?.setNewInstance(state.indexList.toMutableList())
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
