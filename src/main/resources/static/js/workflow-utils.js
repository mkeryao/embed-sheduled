/**
 * 用于处理工作流数据的工具函数集合
 * 提供增强的工作流JSON解析和格式化功能
 */

// 保留原始formatWorkflowJSON函数的引用，以便后续增强
const originalFormatWorkflowJSON = (typeof window !== 'undefined' && window.formatWorkflowJSON) ? window.formatWorkflowJSON : null;

/**
 * 增强版工作流JSON格式化函数
 * @param {string|object} input - 输入的JSON字符串或对象
 * @param {string} type - 数据类型，可以是'nodes', 'edges'或'params'
 * @returns {object} 处理后的JavaScript对象
 */
window.formatWorkflowJSON = function(input, type) {
    // 首先尝试使用原始函数处理
    try {
        if (originalFormatWorkflowJSON) {
            return originalFormatWorkflowJSON(input, type);
        }
    } catch (origError) {
        console.warn('原始处理函数失败，使用增强功能:', origError);
    }
    
    // 增强处理逻辑
    try {
        // 如果输入为空，返回适当的默认值
        if (!input) {
            return type === 'params' ? {} : [];
        }
        
        // 如果输入已经是对象(或数组)，不需要解析
        if (typeof input !== 'string') {
            return input;
        }
        
        // 简单清理
        const cleanedInput = input.trim();
        if (cleanedInput.length === 0) {
            return type === 'params' ? {} : [];
        }
        
        // 使用更可靠的JSON解析方法
        try {
            return JSON.parse(cleanedInput);
        } catch (parseError) {
            console.warn(`工作流${type}解析失败，尝试修复:`, parseError);
            
            // 进行深度修复
            const fixedJson = repairJson(cleanedInput, type);
            try {
                return JSON.parse(fixedJson);
            } catch (fixError) {
                console.error(`修复后仍无法解析${type}数据:`, fixError);
                
                // 创建默认结果
                if (type === 'nodes') {
                    return [{nodeId: "default", nodeName: "默认节点"}];
                } else if (type === 'edges') {
                    return [];
                } else {
                    return {};
                }
            }
        }
    } catch (e) {
        console.error('处理工作流JSON时出错:', e);
        return type === 'params' ? {} : [];
    }
};

/**
 * 修复损坏的JSON字符串
 * @param {string} jsonString - 需要修复的JSON字符串
 * @param {string} type - 数据类型
 * @returns {string} 修复后的JSON字符串
 */
function repairJson(jsonString, type) {
    let result = jsonString;
    
    // 确保基本的JSON结构正确
    if (type !== 'params' && !result.startsWith('[')) {
        result = '[' + result;
    }
    if (type !== 'params' && !result.endsWith(']')) {
        result = result + ']';
    }
    if (type === 'params' && !result.startsWith('{')) {
        result = '{' + result;
    }
    if (type === 'params' && !result.endsWith('}')) {
        result = result + '}';
    }
    
    // 修复常见格式问题
    result = result
        // 修复不带引号的键名
        .replace(/(\{|\,)\s*([a-zA-Z0-9_]+)\s*\:/g, '$1"$2":')
        // 修复单引号
        .replace(/'/g, '"')
        // 修复数字键作为节点ID
        .replace(/"nodeId"\s*:\s*(\d+)/g, '"nodeId":"$1"')
        .replace(/"fromNodeId"\s*:\s*(\d+)/g, '"fromNodeId":"$1"')
        .replace(/"toNodeId"\s*:\s*(\d+)/g, '"toNodeId":"$1"')
        // 修复尾部逗号
        .replace(/,\s*\}/g, '}')
        .replace(/,\s*\]/g, ']');
    
    console.log(`已修复${type}工作流JSON`);
    return result;
}

/**
 * 安全读取工作流DAG图数据，返回简化版本以防出错
 * 特别用于处理复杂或循环引用的工作流图
 */
window.safeReadWorkflowDAG = function(nodesStr, edgesStr) {
    try {
        const nodes = window.formatWorkflowJSON(nodesStr, 'nodes');
        const edges = window.formatWorkflowJSON(edgesStr, 'edges');
        return { nodes, edges };
    } catch (e) {
        console.error('读取工作流数据失败，返回安全默认值:', e);
        return {
            nodes: [{ nodeId: "start", nodeName: "开始节点(恢复模式)" }],
            edges: []
        };
    }
};

// 导出工具函数供其他模块使用
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        formatWorkflowJSON: typeof window !== 'undefined' ? window.formatWorkflowJSON : null,
        safeReadWorkflowDAG: typeof window !== 'undefined' ? window.safeReadWorkflowDAG : null
    };
}
