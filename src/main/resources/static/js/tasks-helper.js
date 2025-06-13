/**
 * tasks-helper.js
 * 任务管理UI的辅助函数，提供可重用的功能
 */

// 避免重复初始化
if (typeof window.tasksHelperInitialized === 'undefined') {
    console.log('初始化tasks helper脚本...');
    window.tasksHelperInitialized = true;

    /**
     * 增强的API错误处理
     * @param {Object} jqXHR - jQuery XHR对象
     * @param {string} textStatus - 状态文本
     * @param {string} errorThrown - 抛出的错误
     * @param {string} context - 错误上下文描述
     */
    window.handleApiError = function(jqXHR, textStatus, errorThrown, context) {
        let errorMessage = '';
        
        // 尝试从响应中获取详细错误信息
        if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
            errorMessage = jqXHR.responseJSON.message;
        } else if (jqXHR.responseText) {
            try {
                const resp = JSON.parse(jqXHR.responseText);
                if (resp.message) {
                    errorMessage = resp.message;
                }
            } catch (e) {
                // 不是JSON格式，使用原始响应文本
                if (jqXHR.responseText.length < 100) {
                    errorMessage = jqXHR.responseText;
                }
            }
        }
        
        // 如果没有提取到有意义的错误信息，使用状态代码和错误文本
        if (!errorMessage) {
            errorMessage = `${jqXHR.status}: ${errorThrown || textStatus}`;
        }
        
        // 记录详细错误到控制台
        console.error(`API错误 (${context}):`, {
            status: jqXHR.status,
            statusText: jqXHR.statusText,
            responseText: jqXHR.responseText,
            errorThrown: errorThrown,
            url: jqXHR.responseURL || '未知URL'
        });
        
        // 显示用户友好的错误提示
        showFeedback(`${context}: ${errorMessage}`, true);
        
        return errorMessage;
    };

    /**
     * 安全调用DAG重绘函数的辅助方法
     * 避免直接引用window.redrawDAG可能导致的未定义错误
     */
    window.safeRedrawDAG = function(enableDragging) {
        if (typeof window.redrawDAG === 'function') {
            try {
                window.redrawDAG(enableDragging);
            } catch (e) {
                console.error('调用redrawDAG时出错:', e);
            }
        } else {
            console.error('redrawDAG函数未定义，请确保tasks-workflow.js已正确加载');
        }
    };

    /**
     * 缩放DAG图的函数
     * @param {number} factor 缩放因子，大于1表示放大，小于1表示缩小
     */
    window.zoomDag = function(factor) {
        if (typeof window.currentScale !== 'number') {
            window.currentScale = 1.0;
        }
        
        const newScale = window.currentScale * factor;
        
        if (newScale < 0.2 || newScale > 3.0) {
            return;
        }
        
        window.currentScale = newScale;
        
        const svg = $('#dagContainer svg');
        if (svg.length) {
            svg.css('transform', `scale(${newScale})`);
            svg.css('transform-origin', 'top left');
            updateZoomFeedback();
        }
    };

    /**
     * 重置DAG图缩放到100%
     */
    window.resetZoom = function() {
        window.currentScale = 1.0;
        const svg = $('#dagContainer svg');
        if (svg.length) {
            svg.css('transform', 'scale(1.0)');
            svg.css('transform-origin', 'top left');
            updateZoomFeedback();
        }
    };

    /**
     * 更新缩放视觉反馈
     */
    window.updateZoomFeedback = function() {
        const zoomPercentage = Math.round(window.currentScale * 100);
        $('#resetZoomBtn').text(`${zoomPercentage}%`);
    };

    console.log('tasks helper脚本初始化完成');
}
