package com.sjl.fund.mvi

import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.entity.UsIndexData
import com.sjl.fund.entity.UsSectorData
import com.sjl.fund.net.UsStockRepository
import kotlinx.coroutines.flow.*

// 用户意图
sealed class UsStockIntent {
    object LoadData : UsStockIntent()
    object Refresh : UsStockIntent()
    object SortSector : UsStockIntent() // 切换板块排序（仅本地重排）
}

// UI 状态
//（data class + StateFlow：loading 作为状态字段；StateFlow 粘性会把最新状态补发给新订阅者，
// error 字段在重新订阅时会重放，下一次加载的新状态会自然覆盖它）
data class UsStockUiState(
    val loading: Boolean = false,
    val indexes: List<UsIndexData> = emptyList(),
    val sectors: List<UsSectorData> = emptyList(),
    val error: String? = null
)

class UsStockViewModel : BaseViewModel() {

    // StateFlow：自带“初始值 + 只保留最新状态”的粘性语义（等效 replay=1 且永不丢首态）
    private val _viewState = MutableStateFlow(UsStockUiState())
    val viewState: StateFlow<UsStockUiState> = _viewState.asStateFlow()

    // 板块排序状态：false=降序(默认)，true=升序
    var sectorAscending = false

    // 缓存原始板块数据（降序），排序时本地重排
    private var cachedSectors: List<UsSectorData> = emptyList()

    // 参考标准 StateFlow MVI 写法：Intent 同步处理，无需 init 里用 SharedFlow 中转收集意图

    /**
     * 分发用户事件
     */
    fun dispatch(intent: UsStockIntent) {
        when (intent) {
            is UsStockIntent.LoadData -> loadData()
            is UsStockIntent.Refresh -> loadData()
            is UsStockIntent.SortSector -> reorderSectors()
        }
    }

    private fun loadData() {
        // StateFlow 用 value 赋值更新当前状态（原子操作、线程安全；tryEmit 的“失败”语义属于 SharedFlow）
        // copy 只置 loading 位，刷新期间旧列表/指数继续展示，不被清空
        _viewState.value = _viewState.value.copy(loading = true, error = null)
        launchUI({
            val indexes = UsStockRepository.loadIndexList()
            cachedSectors = UsStockRepository.loadSectorList()
            _viewState.value = _viewState.value.copy(
                loading = false,
                indexes = indexes,
                sectors = applySort(cachedSectors),
                error = null
            )
        }, {}, { e ->
            LogUtils.e("加载美股数据失败", e)
            // 失败时保留旧数据，仅携带错误信息
            _viewState.value = _viewState.value.copy(loading = false, error = e.message)
        })
    }

    /**
     * 排序切换：仅本地重排板块，不重新请求指数
     */
    private fun reorderSectors() {
        if (cachedSectors.isEmpty()) return
        // StateFlow 直接读当前状态，copy 仅更新板块，保留指数等其他字段
        _viewState.value = _viewState.value.copy(sectors = applySort(cachedSectors))
    }

    private fun applySort(list: List<UsSectorData>): List<UsSectorData> {
        return if (sectorAscending) {
            list.sortedBy { it.changePercent }
        } else {
            list.sortedByDescending { it.changePercent }
        }
    }
}
