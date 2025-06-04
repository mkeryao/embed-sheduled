/**
 * workflow-function-patch.js
 * 这个脚本修复在tasks.html中发现的函数定义冲突问题
 */

// 等待页面DOM完全加载
document.addEventListener('DOMContentLoaded', function() {
    console.log('工作流函数补丁已应用');

    // 创建一个监听器，在页面完全加载后(包括脚本)应用补丁
    window.addEventListener('load', function() {
        applyWorkflowFunctionPatch();
    });
});

/**
 * 应用工作流函数补丁
 * 这个函数会重载页面上的关键工作流处理函数，确保不会出现重复定义
 */
function applyWorkflowFunctionPatch() {
    try {
        console.log('正在应用工作流函数补丁');
        
        // 如果已经从workflow-node-edge-handler.js加载了函数，则使用它们
        if (window.WorkflowNodeEdgeHandler) {
            console.log('使用工作流节点边处理器中的函数');
            
            // 复制函数到全局作用域
            window.addWorkflowNodeItem = window.WorkflowNodeEdgeHandler.addWorkflowNodeItem;
            window.addWorkflowEdgeItem = window.WorkflowNodeEdgeHandler.addWorkflowEdgeItem;
            window.updateNodeSelectors = window.WorkflowNodeEdgeHandler.updateNodeSelectors;
            window.updateEdgeNodeSelectors = window.WorkflowNodeEdgeHandler.updateEdgeNodeSelectors;
            window.updateWorkflowNodesJson = window.WorkflowNodeEdgeHandler.updateWorkflowNodesJson;
            window.updateWorkflowEdgesJson = window.WorkflowNodeEdgeHandler.updateWorkflowEdgesJson;
        } else {
            console.warn('工作流节点边处理器未加载，某些功能可能无法正常工作');
        }
        
        // 增强工作流更新函数，防止栈溢出
        enhanceWorkflowUpdateFunction();
        
        console.log('工作流函数补丁应用完成');
        
        // 为了安全起见，在补丁应用后初始化一次节点选择器
        if (typeof updateNodeSelectors === 'function') {
            try {
                updateNodeSelectors();
            } catch (e) {
                console.warn('更新节点选择器失败:', e);
            }
        }
    } catch (e) {
        console.error('应用工作流函数补丁时出错:', e);
    }
}

/**
 * 增强工作流更新函数，防止栈溢出
 */
function enhanceWorkflowUpdateFunction() {
    // 备份原始更新函数（如果存在）
    if (typeof window.updateWorkflowDag === 'function') {
        const originalUpdateWorkflowDag = window.updateWorkflowDag;
        
        // 替换为增强版本
        window.updateWorkflowDag = function() {
            try {
                // 添加最大调用深度限制
                if (window.workflowUpdateDepth === undefined) {
                    window.workflowUpdateDepth = 0;
                }
                
                // 防止递归调用过深
                if (window.workflowUpdateDepth > 2) {
                    console.warn('检测到工作流更新递归调用过深，已中断');
                    window.workflowUpdateDepth = 0;
                    return false;
                }
                
                // 增加深度计数
                window.workflowUpdateDepth++;
                
                // 调用原始函数
                const result = originalUpdateWorkflowDag.apply(this, arguments);
                
                // 减少深度计数
                window.workflowUpdateDepth--;
                
                return result;
            } catch (e) {
                // 重置深度计数
                window.workflowUpdateDepth = 0;
                console.error('工作流DAG更新失败:', e);
                return false;
            }
        };
        
        console.log('工作流DAG更新函数已增强，防止栈溢出');
    }
}
