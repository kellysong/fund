package com.sjl.fund.mvi

import androidx.lifecycle.viewModelScope
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.entity.UsIndexData
import com.sjl.fund.entity.UsSectorData
import com.sjl.fund.net.UsStockRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// 用户意图
sealed class UsStockIntent {
    object LoadData : UsStockIntent()
    object Refresh : UsStockIntent()
    object SortSector : UsStockIntent() // 切换板块排序（仅本地重排）
}

// UI 状态
data class UsStockUiState(
    val loading: Boolean = false,
    val indexes: List<UsIndexData> = emptyList(),
    val sectors: List<UsSectorData> = emptyList(),
    val error: String? = null
)

class UsStockViewModel : BaseViewModel() {

    private val _viewState = MutableSharedFlow<UsStockUiState>(replay = 1)
    val viewState: SharedFlow<UsStockUiState> = _viewState

    private val userIntent = MutableSharedFlow<UsStockIntent>()

    // 板块排序状态：false=降序(默认)，true=升序
    var sectorAscending = false

    // 缓存原始板块数据（降序），排序时本地重排
    private var cachedSectors: List<UsSectorData> = emptyList()

    init {
        viewModelScope.launch {
            userIntent.collect { intent ->
                when (intent) {
                    is UsStockIntent.LoadData -> loadData()
                    is UsStockIntent.Refresh -> loadData()
                    is UsStockIntent.SortSector -> reorderSectors()
                }
            }
        }
        dispatch(UsStockIntent.LoadData)
    }

    fun dispatch(intent: UsStockIntent) {
        viewModelScope.launch { userIntent.emit(intent) }
    }

    private fun loadData() {
        _viewState.tryEmit(UsStockUiState(loading = true))
        launchUI({
            val indexes = UsStockRepository.loadIndexList()
            cachedSectors = UsStockRepository.loadSectorList()
            _viewState.tryEmit(
                UsStockUiState(
                    loading = false,
                    indexes = indexes,
                    sectors = applySort(cachedSectors)
                )
            )
        }, {}, { e ->
            LogUtils.e("加载美股数据失败", e)
            _viewState.tryEmit(UsStockUiState(loading = false, error = e.message))
        })
    }

    /**
     * 排序切换：仅本地重排板块，不重新请求指数
     */
    private fun reorderSectors() {
        if (cachedSectors.isEmpty()) return
        // 保持当前指数的最后状态，只更新板块
        val indexes = (viewState as kotlinx.coroutines.flow.SharedFlow<UsStockUiState>).replayCache
            .firstOrNull()?.indexes ?: emptyList()
        _viewState.tryEmit(
            UsStockUiState(
                loading = false,
                indexes = indexes,
                sectors = applySort(cachedSectors)
            )
        )
    }

    private fun applySort(list: List<UsSectorData>): List<UsSectorData> {
        return if (sectorAscending) {
            list.sortedBy { it.changePercent }
        } else {
            list.sortedByDescending { it.changePercent }
        }
    }
}
