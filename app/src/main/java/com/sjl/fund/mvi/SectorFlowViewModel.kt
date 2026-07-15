package com.sjl.fund.mvi

import androidx.lifecycle.viewModelScope
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.entity.SectorFlowData
import com.sjl.fund.net.SectorFlowRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 板块主力资金流向 ViewModel (MVI)
 * @author Kelly
 * @version 1.0.0
 */
class SectorFlowViewModel : BaseViewModel() {

    private val _viewState = MutableSharedFlow<SectorFlowUiState>()
    val viewState: SharedFlow<SectorFlowUiState>
        get() = _viewState

    private val userIntent = MutableSharedFlow<SectorFlowIntent>()

    // 当前选中的板块分类
    var currentBoard: String = "行业"

    init {
        viewModelScope.launch {
            userIntent.collect {
                when (it) {
                    is SectorFlowIntent.LoadSectorData -> loadSectorData(it.board)
                    is SectorFlowIntent.SwitchBoard -> {
                        currentBoard = it.board
                        loadSectorData(it.board)
                    }
                    is SectorFlowIntent.RefreshData -> loadSectorData(currentBoard)
                }
            }
        }
    }

    /**
     * 分发用户事件
     */
    fun dispatch(intent: SectorFlowIntent) {
        try {
            viewModelScope.launch {
                userIntent.emit(intent)
            }
        } catch (e: Exception) {
            LogUtils.e("dispatch error", e)
        }
    }

    private fun loadSectorData(board: String) {
        launchUI({
            val fs = SectorFlowRepository.BOARD_FS_MAP[board] ?: "m:90+t:2"
            val list = SectorFlowRepository.loadSectorFlow(fs)
            _viewState.emit(SectorFlowUiState.LoadSuccess(board, list))
        }, {}, { e ->
            LogUtils.e("加载板块资金流失败", e)
            _viewState.emit(SectorFlowUiState.LoadError(e))
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
 * UI 状态
 */
sealed class SectorFlowUiState {
    data class LoadSuccess(val board: String, val data: List<SectorFlowData>) : SectorFlowUiState()
    data class LoadError(val error: Throwable) : SectorFlowUiState()
}
