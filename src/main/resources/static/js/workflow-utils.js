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
    // 防止栈溢出：设置最大字符长度限制
    const MAX_INPUT_LENGTH = 1000000; // 1MB限制
    
    // 如果输入超过限制，直接拒绝处理
    if (typeof input === 'string' && input.length > MAX_INPUT_LENGTH) {
        console.error(`工作流${type}数据过大(${input.length}字节)，超出限制(${MAX_INPUT_LENGTH}字节)`);
        throw new Error(`数据过大(${Math.round(input.length/1024)}KB)，请减小内容`);
    }
    
    // 首先尝试使用安全模式返回空结果
    if (!input) {
        return type === 'params' ? {} : [];
    }
    
    // 如果输入已经是对象(或数组)，直接返回
    if (typeof input !== 'string') {
        return input;
    }
    
    // 简单清理
    const cleanedInput = input.trim();
    if (cleanedInput.length === 0) {
        return type === 'params' ? {} : [];
    }
    
    // 使用更可靠的JSON解析方法，带异常处理
    try {
        // 首先尝试直接解析，这是最快的方法
        try {
            return JSON.parse(cleanedInput);
        } catch (directParseError) {
            // 直接解析失败，尝试修复
            console.warn(`工作流${type}直接解析失败，尝试修复:`, directParseError);
            
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
 * 修复损坏的JSON字符串（安全版本）
 * @param {string} jsonString - 需要修复的JSON字符串
 * @param {string} type - 数据类型
 * @returns {string} 修复后的JSON字符串
 */
function repairJson(jsonString, type) {
    // 安全检查，限制输入长度，防止栈溢出
    const MAX_REPAIR_LENGTH = 100000; // 100KB
    if (jsonString.length > MAX_REPAIR_LENGTH) {
        console.error(`JSON修复输入过大(${jsonString.length})，截断至${MAX_REPAIR_LENGTH}`);
        jsonString = jsonString.substring(0, MAX_REPAIR_LENGTH);
    }
    
    // 检测潜在的特殊字符和恶意输入
    if (/(<script|<iframe|eval\(|Function\()/i.test(jsonString)) {
        console.error("检测到潜在不安全JSON输入");
        return type === 'params' ? '{}' : '[]';
    }
    
    try {
        let result = jsonString;
        
        // 1. 处理空数据
        if (!result.trim()) {
            return type === 'params' ? '{}' : '[]';
        }
        
        // 2. 确保基本的JSON结构正确
        if (type !== 'params') {
            // 数组类型
            if (!result.trim().startsWith('[')) {
                result = '[' + result.trim();
            }
            if (!result.trim().endsWith(']')) {
                result = result.trim() + ']';
            }
        } else {
            // 对象类型
            if (!result.trim().startsWith('{')) {
                result = '{' + result.trim();
            }
            if (!result.trim().endsWith('}')) {
                result = result.trim() + '}';
            }
        }
        
        // 3. 简单的格式修复（只进行安全的替换）
        // 修复常见的JSON语法错误
        result = result
            // 修复不带引号的键名（安全版本）
            .replace(/(\{|\,)\s*([a-zA-Z0-9_]+)\s*\:/g, '$1"$2":')
            // 修复单引号为双引号
            .replace(/(\{|\,|\:)\s*'([^']*?)'\s*([,\}\]])/g, '$1"$2"$3')
            // 修复数字键作为节点ID (确保ID为字符串类型)
            .replace(/"nodeId"\s*:\s*(\d+)/g, '"nodeId":"$1"')
            .replace(/"fromNodeId"\s*:\s*(\d+)/g, '"fromNodeId":"$1"')
            .replace(/"toNodeId"\s*:\s*(\d+)/g, '"toNodeId":"$1"')
            // 修复尾部逗号
            .replace(/,\s*\}/g, '}')
            .replace(/,\s*\]/g, ']');
        
        // 4. 检验修复后的JSON是否有效
        try {
            // 尝试解析，验证结果
            JSON.parse(result);
            console.log(`成功修复${type}工作流JSON`);
            return result;
        } catch (checkError) {
            console.warn(`修复后仍有错误，返回默认值:`, checkError);
            // 返回安全的默认值
            return type === 'params' ? '{}' : '[]';
        }
    } catch (repairError) {
        console.error(`JSON修复过程出错:`, repairError);
        // 出错时返回安全的默认值
        return type === 'params' ? '{}' : '[]';
    }
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
