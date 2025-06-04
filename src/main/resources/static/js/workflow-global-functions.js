/**
 * 工作流全局函数合并工具
 * 用于解决页面加载过程中的函数冲突和使用问题
 */

// 在所有其他脚本加载完成后执行
window.addEventListener('load', function() {
    console.log('正在确保工作流函数全局可用...');
    
    try {
        // 确保添加节点函数可用
        ensureGlobalFunction('addWorkflowNodeItem', window.WorkflowNodeEdgeHandler && window.WorkflowNodeEdgeHandler.addWorkflowNodeItem);
        
        // 确保添加边函数可用
        ensureGlobalFunction('addWorkflowEdgeItem', window.WorkflowNodeEdgeHandler && window.WorkflowNodeEdgeHandler.addWorkflowEdgeItem);
        
        // 确保更新节点选择器函数可用
        ensureGlobalFunction('updateNodeSelectors', window.WorkflowNodeEdgeHandler && window.WorkflowNodeEdgeHandler.updateNodeSelectors);
        
        // 确保更新特定边的节点选择器函数可用
        ensureGlobalFunction('updateEdgeNodeSelectors', window.WorkflowNodeEdgeHandler && window.WorkflowNodeEdgeHandler.updateEdgeNodeSelectors);
        
        // 确保更新工作流节点JSON函数可用
        ensureGlobalFunction('updateWorkflowNodesJson', window.WorkflowNodeEdgeHandler && window.WorkflowNodeEdgeHandler.updateWorkflowNodesJson);
        
        // 确保更新工作流边JSON函数可用
        ensureGlobalFunction('updateWorkflowEdgesJson', window.WorkflowNodeEdgeHandler && window.WorkflowNodeEdgeHandler.updateWorkflowEdgesJson);
        
        // 固定safeFormatWorkflowJSON实现
        ensureSafeFormatWorkflowJSON();
        
        console.log('所有工作流函数现在全局可用');
    } catch (error) {
        console.error('确保工作流函数全局可用时出错:', error);
    }
    
    // 处理特殊的ID=8工作流问题，适用于历史遗留数据
    fixWorkflow8IfNeeded();
});

/**
 * 确保指定的函数在全局作用域中可用
 * @param {string} funcName - 函数名
 * @param {Function} preferredImpl - 首选实现
 */
function ensureGlobalFunction(funcName, preferredImpl) {
    // 如果提供了首选实现并且它是函数，则使用它
    if (typeof preferredImpl === 'function') {
        window[funcName] = preferredImpl;
        console.log(`已使用首选实现设置全局函数: ${funcName}`);
        return;
    }
    
    // 检查是否已经有函数存在
    if (typeof window[funcName] === 'function') {
        console.log(`全局函数 ${funcName} 已存在`);
        return;
    }
    
    // 如果函数仍然不可用，创建一个空的实现并记录错误
    window[funcName] = function() {
        console.error(`警告: ${funcName} 的实现丢失，请重新加载页面`);
        return null;
    };
    
    console.warn(`为 ${funcName} 创建了临时占位实现`);
}

/**
 * 确保safeFormatWorkflowJSON实现是安全的，防止栈溢出
 */
function ensureSafeFormatWorkflowJSON() {
    // 备份原始函数（如果存在）
    const originalFn = window.safeFormatWorkflowJSON;
    
    // 替换为简单安全的实现
    window.safeFormatWorkflowJSON = function(input, type) {
        try {
            // 如果输入为空，返回默认值
            if (!input) {
                return type === 'params' ? {} : [];
            }
            
            // 如果输入已经是对象，直接返回
            if (typeof input === 'object' && input !== null) {
                return input;
            }
            
            // 字符串处理
            if (typeof input === 'string') {
                const trimmed = input.trim();
                if (trimmed === '') {
                    return type === 'params' ? {} : [];
                }
                
                try {
                    return JSON.parse(trimmed);
                } catch (e) {
                    console.warn(`JSON解析错误 (${type}): ${e.message}`);
                    return type === 'params' ? {} : [];
                }
            }
            
            return type === 'params' ? {} : [];
        } catch (e) {
            console.error('safeFormatWorkflowJSON处理异常:', e);
            return type === 'params' ? {} : [];
        }
    };
    
    console.log('已确保safeFormatWorkflowJSON的安全实现');
}

/**
 * 修复特殊的ID=8工作流问题（如果需要）
 * 此函数仅针对已知的工作流ID=8有问题的情况
 */
function fixWorkflow8IfNeeded() {
    // 检查是否存在此函数（这是一个已知问题的指示器）
    if (typeof window.applyWorkflow8SafeMode === 'function') {
        console.log('检测到ID=8工作流安全模式函数，准备应用');
        
        // 添加一个延迟的检查器，尝试应用安全模式
        setTimeout(function() {
            const taskId = $('#taskId').val();
            if (taskId === '8') {
                console.log('检测到正在编辑ID=8工作流，应用安全模式');
                try {
                    window.applyWorkflow8SafeMode();
                } catch (e) {
                    console.error('应用ID=8工作流安全模式失败:', e);
                }
            } else {
                console.log('未检测到ID=8工作流，跳过安全模式');
            }
        }, 1000); // 延迟1秒检查
    }
}
