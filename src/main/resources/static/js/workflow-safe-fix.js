// 标记原始的safeFormatWorkflowJSON函数是否已被保存
var originalSafeFormatWorkflowJSON = null;
var isSafeFunctionInitialized = false;

// 增强版safeFormatWorkflowJSON，专门用于避免栈溢出错误
(function() {
  console.log("注入优化版工作流JSON格式化函数");
  
  // 避免重复初始化
  if (isSafeFunctionInitialized) {
    console.log("安全函数已初始化，跳过");
    return;
  }
  
  // 保存原始函数（如果存在）
  if (typeof window.safeFormatWorkflowJSON === "function") {
    originalSafeFormatWorkflowJSON = window.safeFormatWorkflowJSON;
    console.log("原始safeFormatWorkflowJSON函数已保存");
  }
  
  // 创建优化版本的安全格式化函数
  window.safeFormatWorkflowJSON = function(input, type, options) {
    // 防止递归和性能问题的标志
    const MAX_SIZE = 1000000; // 1MB限制
    
    // 记录日志但避免过多控制台输出
    if (Math.random() < 0.1) { // 只记录约10%的调用
      console.log(`处理工作流${type}数据 [优化版]`);
    }
    
    try {
      // 1. 快速路径：空值处理
      if (!input) {
        return type === "params" ? {} : [];
      }
      
      // 2. 快速路径：已经是对象的情况
      if (typeof input === "object" && input !== null) {
        return input;
      }
      
      // 3. 字符串处理
      if (typeof input === "string") {
        // 3.1 空字符串检查
        const trimmed = input.trim();
        if (trimmed === "") {
          return type === "params" ? {} : [];
        }
        
        // 3.2 大小检查，防止处理过大的输入导致性能问题
        if (trimmed.length > MAX_SIZE) {
          console.error(`工作流${type}数据过大(${trimmed.length}字节)，已截断`);
          // 返回安全的默认值而不是尝试处理可能导致栈溢出的大型输入
          return type === "params" ? {} : [];
        }
        
        // 3.3 直接JSON解析
        try {
          return JSON.parse(trimmed);
        } catch (parseError) {
          // 记录错误但不尝试复杂修复
          console.warn(`工作流${type}数据解析错误: ${parseError.message}`);
          
          // 基本清理尝试
          try {
            // 只进行最简单的修复，确保数据结构正确
            let fixed = trimmed;
            
            // 确保起始/结尾格式正确
            if (type === "params") {
              if (!fixed.startsWith("{")) fixed = "{" + fixed;
              if (!fixed.endsWith("}")) fixed = fixed + "}";
            } else {
              if (!fixed.startsWith("[")) fixed = "[" + fixed;
              if (!fixed.endsWith("]")) fixed = fixed + "]";
            }
            
            // 尝试解析
            return JSON.parse(fixed);
          } catch (fixError) {
            // 修复尝试失败，返回安全值
            console.error(`无法修复工作流${type}数据格式: ${fixError.message}`);
            return type === "params" ? {} : [];
          }
        }
      }
      
      // 4. 默认返回安全值
      return type === "params" ? {} : [];
    } catch (e) {
      // 捕获所有异常，确保函数始终返回安全值
      console.error(`safeFormatWorkflowJSON处理异常: ${e.message}`);
      return type === "params" ? {} : [];
    }
  };
  
  // 标记初始化完成
  isSafeFunctionInitialized = true;
  console.log("优化版工作流JSON格式化函数已注入");
})();
