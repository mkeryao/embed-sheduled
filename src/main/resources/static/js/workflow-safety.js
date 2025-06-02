/**
 * workflow-safety.js
 * 工作流安全处理工具
 * 防止栈溢出和其他JSON解析问题的专用工具
 */

(function() {
    /**
     * 安全包装formatWorkflowJSON函数，防止栈溢出
     * @param {string|object} input - 输入的JSON字符串或对象
     * @param {string} type - 数据类型，可以是'nodes', 'edges'或'params'
     * @param {Object} options - 额外配置选项
     * @returns {object} 处理后的JavaScript对象
     */
    window.safeFormatWorkflowJSON = function(input, type, options) {
        // 默认配置
        const config = Object.assign({
            maxInputLength: 1000000, // 1MB
            defaultValue: type === 'params' ? {} : [],
            enableCircularDetection: true,
            enableSizeRestriction: true,
            retryCount: 2  // 最大尝试次数
        }, options || {});
        
        // 捕获所有可能的错误
        try {
            console.debug(`开始安全处理工作流${type}数据`);
            
            // 如果输入为空，返回适当的默认值
            if (!input) {
                return config.defaultValue;
            }
            
            // 如果输入已经是对象(或数组)，进行循环引用检查后返回
            if (typeof input !== 'string') {
                if (config.enableCircularDetection) {
                    try {
                        // 检测循环引用
                        JSON.stringify(input);
                        return input;
                    } catch (e) {
                        // 如果存在循环引用，尝试移除循环引用
                        console.warn(`工作流${type}数据中检测到循环引用，尝试修复`);
                        return window.decycleObject ? window.decycleObject(input) : config.defaultValue;
                    }
                }
                return input;
            }
            
            // 如果是字符串，检查长度是否过大
            if (config.enableSizeRestriction && input.length > config.maxInputLength) {
                console.error(`工作流${type}数据过大(${input.length})，超过限制(${config.maxInputLength})`);
                return config.defaultValue;
            }
            
            // 使用正式格式化函数，如果可用
            if (typeof window.formatWorkflowJSON === 'function') {
                try {
                    return window.formatWorkflowJSON(input, type);
                } catch (formatError) {
                    console.warn(`使用formatWorkflowJSON函数处理工作流${type}失败:`, formatError);
                    // 继续使用其他方法
                }
            }
            
            // 退回到标准解析
            try {
                if (input.trim().length === 0) {
                    return config.defaultValue;
                }
                
                return JSON.parse(input);
            } catch (parseError) {
                console.warn(`标准JSON.parse解析工作流${type}失败，尝试修复:`, parseError);
                
                // 最后尝试修复简单错误并解析
                let attempts = 0;
                let fixed = input.trim();
                
                while (attempts < config.retryCount) {
                    attempts++;
                    try {
                        // 应用基础修复
                        fixed = fixCommonJsonIssues(fixed, type);
                        const result = JSON.parse(fixed);
                        console.log(`在第${attempts}次修复尝试后成功解析工作流${type}`);
                        return result;
                    } catch (e) {
                        console.warn(`第${attempts}次修复尝试失败:`, e);
                    }
                }
                
                // 所有尝试失败，返回默认值
                console.error(`所有修复尝试都失败，返回默认工作流${type}`);
                return config.defaultValue;
            }
        } catch (unexpectedError) {
            console.error(`safeFormatWorkflowJSON处理过程中发生意外错误:`, unexpectedError);
            return config.defaultValue;
        }
    };
    
    /**
     * 修复常见的JSON格式问题
     * @param {string} json - JSON字符串
     * @param {string} type - 数据类型
     * @returns {string} 修复后的JSON字符串
     */
    function fixCommonJsonIssues(json, type) {
        // 避免处理null或undefined
        if (!json) return type === 'params' ? '{}' : '[]';
        
        let fixed = json;
        
        // 1. 处理单引号替换为双引号
        fixed = fixed.replace(/'/g, '"');
        
        // 2. 确保对象属性键名有引号
        fixed = fixed.replace(/(\s*?{\s*?|\s*?,\s*?)(['"])?([a-zA-Z0-9_]+)(['"])?:/g, '$1"$3":');
        
        // 3. 修复类型特定问题
        if (type === 'nodes') {
            // 确保节点ID是字符串
            fixed = fixed.replace(/"nodeId"\s*:\s*(\d+)([,}])/g, '"nodeId":"$1"$2');
            
            // 确保taskConfigId是整数
            fixed = fixed.replace(/"taskConfigId"\s*:\s*"(\d+)"([,}])/g, '"taskConfigId":$1$2');
        } else if (type === 'edges') {
            // 确保fromNodeId和toNodeId是字符串
            fixed = fixed.replace(/"fromNodeId"\s*:\s*(\d+)([,}])/g, '"fromNodeId":"$1"$2');
            fixed = fixed.replace(/"toNodeId"\s*:\s*(\d+)([,}])/g, '"toNodeId":"$1"$2');
            
            // 确保priority是整数
            fixed = fixed.replace(/"priority"\s*:\s*"(\d+)"([,}])/g, '"priority":$1$2');
        }
        
        // 4. 修复前后缺少括号的问题
        if (type === 'params') {
            if (!fixed.trim().startsWith('{')) fixed = '{' + fixed.trim();
            if (!fixed.trim().endsWith('}')) fixed = fixed.trim() + '}';
        } else {
            if (!fixed.trim().startsWith('[')) fixed = '[' + fixed.trim();
            if (!fixed.trim().endsWith(']')) fixed = fixed.trim() + ']';
        }
        
        // 5. 修复尾部多余的逗号
        fixed = fixed.replace(/,\s*}/g, '}').replace(/,\s*\]/g, ']');
        
        // 6. 确保JSON格式正确
        fixed = fixed.replace(/}\s*{/g, '},{');
        
        return fixed;
    }
    
    // 在window对象上暴露工具函数
    window.WorkflowSafety = {
        safeFormatWorkflowJSON: window.safeFormatWorkflowJSON,
        fixCommonJsonIssues: fixCommonJsonIssues
    };
    
    console.log('工作流安全处理工具已加载');
})();
