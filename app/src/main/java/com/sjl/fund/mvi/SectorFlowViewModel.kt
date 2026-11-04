package com.sjl.fund.mvi

import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.entity.SectorFlowData
import com.sjl.fund.net.SectorFlowRepository
import kotlinx.coroutines.flow.*

/**
 * 板块主力资金流向 ViewModel (MVI)
 * @author Kelly
 * @version 1.0.0
 */
class SectorFlowViewModel : BaseViewModel() {

    // StateFlow：自带“初始值 + 只保留最新状态”的粘性语义（等效 replay=1 且永不丢首态），
    // 订阅建立时先收到当前状态，之后每个新状态即时送达
    private val _viewState = MutableStateFlow(SectorFlowUiState())
    val viewState: StateFlow<SectorFlowUiState>
        get() = _viewState

    // 当前选中的板块分类
    var currentBoard: String = "行业"
    // 排序方向：false=降序(默认)，true=升序
    var isAscending: Boolean = false

    /**
     * 分发用户事件（参考标准 StateFlow MVI 写法：Intent 同步处理，
     * 无需 init 里用 SharedFlow 中转收集意图）
     */
    fun dispatch(intent: SectorFlowIntent) {
        when (intent) {
            is SectorFlowIntent.LoadSectorData -> {
                currentBoard = intent.board
                loadSectorData(intent.board)
            }
            is SectorFlowIntent.SwitchBoard -> {
                currentBoard = intent.board
                loadSectorData(intent.board)
            }
            is SectorFlowIntent.RefreshData -> loadSectorData(currentBoard)
        }
    }

    private fun loadSectorData(board: String) {
        // StateFlow 用 value 赋值更新当前状态（原子操作、线程安全；
        // tryEmit 的“可能失败”语义属于 SharedFlow，StateFlow 下与赋值等价但不惯用）
        // copy 只置 loading 位，保留旧 data（刷新/切 Tab 期间旧列表继续展示，不被清空）
        _viewState.value = _viewState.value.copy(loading = true, board = board, error = null)
        launchUI({
            val fs = SectorFlowRepository.BOARD_FS_MAP[board] ?: "m:90+t:2"
            val po = if (isAscending) 0 else 1
            val list = SectorFlowRepository.loadSectorFlow(fs, po)
            _viewState.value = _viewState.value.copy(
                loading = false,
                board = board,
                data = list,
                error = null
            )
        }, {}, { e ->
            LogUtils.e("加载板块资金流失败", e)
            // 失败时同样保留旧数据，仅携带错误信息
            _viewState.value = _viewState.value.copy(
                loading = false,
                board = board,
                error = e.message
            )
        })
    }
}

/**
 * 用户意图
 */
sealed class SectorFlowIntent {
    data class LoadSectorData(val board: String) : SectorFlowIntent()
    data class SwitchBoard(val board: String) : SectorFlowIntent()
    object RefreshData : SectorFlowIntent()
}

/**
 * UI 状态（data class + StateFlow：loading 作为状态字段，刷新时旧 data 可共存；
 * 注意 StateFlow 粘性会把最新状态补发给新订阅者，error 字段会在重新订阅时重放，
 * 下一次加载的新状态会自然覆盖它）
 */
data class SectorFlowUiState(
    val loading: Boolean = false,
    val board: String = "",
    val data: List<SectorFlowData> = emptyList(),
    val error: String? = null
)
