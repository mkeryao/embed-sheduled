// API Base URL
const API_BASE_URL = '/api'; // Adjust if your context path is different

/**
 * Makes an authenticated API call.
 * @param {string} method - HTTP method (GET, POST, PUT, DELETE)
 * @param {string} endpoint - API endpoint (e.g., /tasks)
 * @param {object|null} data - Data to send (for POST, PUT)
 * @param {function} onSuccess - Callback on success
 * @param {function} onError - Callback on error
 */
function makeApiCall(method, endpoint, data, onSuccess, onError) {
    const token = localStorage.getItem('jwtToken');
    if (!token && endpoint !== '/auth/login') { // Allow login without token
        // No token, redirect to login, unless it's the login page itself trying to log in
        if (window.location.pathname !== '/login.html' && window.location.pathname !== '/') {
            logout(); // Clear any partial session data and redirect
            return;
        }
    }    // 智能处理API路径：
    // 1. 如果已以'/api'开头，则直接使用
    // 2. 如果不是以'/'开头，添加'/'
    // 3. 然后添加API_BASE_URL前缀
    let apiUrl = endpoint;
    if (!apiUrl.startsWith('/api')) {
        if (!apiUrl.startsWith('/')) {
            apiUrl = '/' + apiUrl;
        }
        apiUrl = API_BASE_URL + apiUrl;
    }    $.ajax({
        url: apiUrl,
        method: method,
        contentType: 'application/json',
        data: data ? JSON.stringify(data) : null,
        headers: {
            'Authorization': 'Bearer ' + token
        },
        dataType: 'text', // 设置为text而不是json，以便手动解析
        success: function(responseText, textStatus, jqXHR) {
            // 尝试安全解析JSON
            if (responseText) {
                try {
                    // 先尝试标准解析
                    const parsedData = JSON.parse(responseText);
                    onSuccess(parsedData);
                } catch(parseError) {
                    // 如果标准解析失败，尝试使用safeParseJSON
                    console.warn('JSON解析失败，尝试修复并解析:', parseError);
                    const safeParsed = safeParseJSON(responseText);
                    if (safeParsed) {
                        console.log('已成功修复并解析JSON');
                        onSuccess(safeParsed);
                    } else {
                        // 如果安全解析也失败，则报错
                        console.error('无法解析响应数据:', responseText);
                        if (onError) {
                            onError(jqXHR, 'parsererror', '无法解析服务器响应');
                        } else {
                            alert('数据解析错误: 服务器响应格式无效');
                        }
                    }
                }
            } else {
                // 空响应但请求成功，一些API可能不返回内容
                onSuccess(null);
            }
        },
        error: function(jqXHR, textStatus, errorThrown) {
            if (jqXHR.status === 401 && window.location.pathname !== '/login.html') { // Unauthorized
                // Token might be invalid or expired
                logout(); // Redirect to login
            } else if (onError) {
                onError(jqXHR, textStatus, errorThrown);
            } else {                // Default error handling
                let message = `错误: ${jqXHR.status} - ${errorThrown}`;
                 if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
                    message = jqXHR.responseJSON.message;
                } else if (jqXHR.responseText) {
                    try {
                        const err = JSON.parse(jqXHR.responseText);
                        if (err.message) message = err.message;
                    } catch(e) { /* ignore parse error */ }
                }
                console.error('API 调用错误:', message);
                alert('发生错误: ' + message);
            }
        }
    });
}

// --- 认证函数 ---
function login(username, password, onSuccess, onError) {
    makeApiCall('POST', '/auth/login', { username, password }, onSuccess, onError);
}

function logout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    // 重定向到登录页面
    const currentPath = window.location.pathname;
    if (currentPath.endsWith('/login.html') || currentPath === '/' || currentPath.endsWith('/index.html')) {
        // 如果已经在登录页或首页，刷新页面
        window.location.reload();
    } else {
        // 否则重定向到登录页
        window.location.href = 'login.html';
    }
}

// --- 工具函数 ---
function getUrlParams() {
    const params = {};
    const queryString = window.location.search.substring(1);
    const regex = /([^&=]+)=([^&]*)/g;
    let m;
    while (m = regex.exec(queryString)) {
        params[decodeURIComponent(m[1])] = decodeURIComponent(m[2]);
    }
    return params;
}

function checkAuth() {
    const token = localStorage.getItem('jwtToken');
    const currentPath = window.location.pathname;
    
    // 检查是否在公共页面
    const isPublicPage = currentPath.endsWith('/login.html') || 
                         currentPath === '/' || 
                         currentPath.endsWith('/index.html');

    if (!token && !isPublicPage) {
        logout(); // 如果不在公开页面且没有令牌，则重定向到登录
    }
    // 如果令牌存在，允许访问。特定页面可能需要进一步验证。
}

// Call checkAuth on page load for non-public pages
$(document).ready(function() {
    const currentPath = window.location.pathname;
    const isPublicPage = currentPath.endsWith('/login.html') || 
                         currentPath === '/' || 
                         currentPath.endsWith('/index.html');
                         
    if (!isPublicPage) {
        checkAuth();
    }

    // Global logout link handler
    $('#logout-link').on('click', function(e) {
        e.preventDefault();
        logout();
    });
});

// 显示通用成功/错误消息的辅助函数
function showFeedback(message, isError = false) {
    const feedbackDiv = $('#feedback-message');
    if (feedbackDiv.length) {
        feedbackDiv.text(message)
                   .removeClass(isError ? 'alert-success' : 'alert-danger')
                   .addClass(isError ? 'alert-danger' : 'alert-success')
                   .show();
        setTimeout(() => feedbackDiv.hide(), 5000); // 5秒后隐藏
    } else {
        alert(message); // 如果没有反馈div，则使用警告框
    }
}

// 日期时间处理工具函数
/**
 * 将日期字符串转换为HTML5 datetime-local格式 (YYYY-MM-DDThh:mm)
 * @param {string|Date} dateString - 日期字符串或Date对象
 * @return {string} - 格式化后的日期时间字符串，如果输入无效则返回空字符串
 */
function formatDateTimeForInput(dateString) {
    if (!dateString) return '';
    
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return ''; // 检查日期是否有效
        
        // 转换为本地时间的ISO格式，并保留到分钟
        // 使用更可靠的方法构建datetime-local格式
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    } catch (e) {
        console.error('日期格式化错误:', e);
        return '';
    }
}

/**
 * 将HTML5 datetime-local格式转换为ISO格式，用于API提交
 * @param {string} inputValue - 从input[type=datetime-local]获取的值
 * @return {string|null} - ISO格式的日期时间字符串，如果输入为空则返回null
 */
function parseInputDateTime(inputValue) {
    if (!inputValue) return null;
    
    try {
        const date = new Date(inputValue);
        if (isNaN(date.getTime())) return null; // 检查日期是否有效
        
        return date.toISOString();
    } catch (e) {
        console.error('日期解析错误:', e);
        return null;
    }
}

/**
 * 格式化日期时间为本地化显示格式
 * @param {string|Date} dateString - 日期字符串或Date对象
 * @param {boolean} includeTime - 是否包含时间部分
 * @return {string} - 格式化后的本地化日期时间字符串，如果输入无效则返回'-'
 */
function formatDateTime(dateString, includeTime = true) {
    if (!dateString) return '-';
    
    try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return '-';
        
        const options = includeTime 
            ? { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }
            : { year: 'numeric', month: '2-digit', day: '2-digit' };
            
        return date.toLocaleDateString('zh-CN', options);
    } catch (e) {
        console.error('日期显示格式化错误:', e);
        return '-';
    }
}

/**
 * 验证日期时间输入控件
 * @param {string} startDateSelector - 开始日期时间选择器
 * @param {string} endDateSelector - 结束日期时间选择器  
 * @param {string} validationMsgSelector - 验证消息容器选择器
 * @return {boolean} - 日期是否有效
 */
function validateDateTimeRange(startDateSelector, endDateSelector, validationMsgSelector) {
    const startDate = $(startDateSelector).val();
    const endDate = $(endDateSelector).val();
    const validationContainer = $(validationMsgSelector);
    
    // 移除之前的验证信息
    validationContainer.empty();
    
    // 如果两个日期都没有填写，则视为有效
    if (!startDate && !endDate) return true;
    
    let isValid = true;
    let errors = [];
    
    // 验证开始日期格式
    if (startDate && !isValidDateTimeFormat(startDate)) {
        errors.push('开始日期格式无效');
        isValid = false;
    }
    
    // 验证结束日期格式
    if (endDate && !isValidDateTimeFormat(endDate)) {
        errors.push('结束日期格式无效');
        isValid = false;
    }
    
    // 如果两个日期都有值且开始日期晚于结束日期
    if (startDate && endDate && new Date(startDate) > new Date(endDate)) {
        errors.push('开始日期不能晚于结束日期');
        isValid = false;
    }
    
    // 如果当前有无效日期，显示错误信息
    if (!isValid) {
        const errorMsg = $(`<div class="text-danger mt-1 small">${errors.join('<br>')}</div>`);
        validationContainer.append(errorMsg);
    } else {
        validationContainer.append('<div class="text-success mt-1 small"><i class="fas fa-check-circle"></i> 日期格式正确</div>');
    }
    
    return isValid;
}

/**
 * 验证日期时间格式是否为有效的HTML5 datetime-local格式
 * @param {string} dateTimeStr - 日期时间字符串
 * @return {boolean} - 是否为有效格式
 */
function isValidDateTimeFormat(dateTimeStr) {
    if (!dateTimeStr) return false;
    
    // 检查是否符合HTML5 datetime-local格式 (YYYY-MM-DDThh:mm)
    const regex = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;
    if (!regex.test(dateTimeStr)) return false;
    
    // 进一步验证日期是否有效
    const date = new Date(dateTimeStr);
    return !isNaN(date.getTime());
}

/**
 * 解析日期时间范围并返回友好显示
 * @param {string|Date} startDate - 开始日期
 * @param {string|Date} endDate - 结束日期
 * @return {string} - 格式化的日期范围显示
 */
function formatDateTimeRange(startDate, endDate) {
    if (!startDate && !endDate) return '-';
    
    let rangeText = '';
    
    if (startDate) {
        const start = formatDateTime(startDate, false);
        rangeText += `从: ${start}`;
    }
    
    if (endDate) {
        const end = formatDateTime(endDate, false);
        rangeText += `${rangeText ? '<br>' : ''}至: ${end}`;
    }
    
    return rangeText || '-';
}

/**
 * 安全解析JSON字符串，处理常见的JSON格式错误
 * @param {string} jsonString - JSON字符串
 * @param {boolean} showFeedback - 是否显示反馈信息
 * @return {object|null} - 解析后的对象，解析失败则返回null
 */
function safeParseJSON(jsonString, showFeedback = true) {
    if (!jsonString || jsonString.trim() === '') {
        return null;
    }
    
    try {
        return JSON.parse(jsonString);
    } catch (e) {
        console.error('JSON解析错误:', e, 'JSON字符串:', jsonString);
        
        // 输出错误位置信息，帮助调试
        const errorPosition = e.message.match(/position (\d+)/);
        if (errorPosition && errorPosition[1]) {
            const position = parseInt(errorPosition[1]);
            console.error(`错误位置附近的字符串: "${jsonString.substring(Math.max(0, position - 10), position)}👉${jsonString.substring(position, Math.min(jsonString.length, position + 10))}"`);
        }
        
        // 尝试修复常见的JSON格式问题
        let correctedJson = jsonString;
        
        // 1. 修复数字键不带引号的问题，如 {0:1,2:2} => {"0":1,"2":2}
        // 使用更精确的正则表达式来处理各种情况下的数字键
        correctedJson = correctedJson.replace(/([{,])\s*(\d+)\s*:/g, '$1"$2":');
        
        // 2. 特别处理taskTypeDistribution对象中的数字键
        const taskTypePattern = /"taskTypeDistribution"\s*:\s*{([^}]*)}/;
        const taskTypeMatch = taskTypePattern.exec(correctedJson);
        
        if (taskTypeMatch && taskTypeMatch[1]) {
            const originalContent = taskTypeMatch[1];
            // 修复数字键
            const fixedContent = originalContent.replace(/(\d+)\s*:/g, '"$1":');
            // 替换回原始字符串
            correctedJson = correctedJson.replace(taskTypePattern, `"taskTypeDistribution":{${fixedContent}}`);
        }
        
        // 3. 修复缺少双引号的键
        correctedJson = correctedJson.replace(/(\s*?{\s*?|\s*?,\s*?)(['"])?([a-zA-Z0-9_]+)(['"])?:/g, '$1"$3":');
        
        // 4. 修复单引号替换为双引号
        correctedJson = correctedJson.replace(/'/g, '"');
        
        // 5. 修复尾部可能的逗号问题
        correctedJson = correctedJson.replace(/,\s*}/g, '}');
        correctedJson = correctedJson.replace(/,\s*\]/g, ']');
        
        console.log('尝试修正后的JSON:', correctedJson);
        
        try {
            const parsed = JSON.parse(correctedJson);
            console.log('JSON已自动修正并解析成功', parsed);
            
            // 安全调用showFeedback函数，确保它存在
            if (showFeedback && typeof window.showFeedback === 'function') {
                window.showFeedback('JSON格式已自动修正', false);
            }
            return parsed;
        } catch(e2) {
            console.error('自动修正JSON失败:', e2, '尝试了以下字符串:', correctedJson);
            
            // 最后的尝试：完全重建taskTypeDistribution对象
            try {
                // 尝试提取主要信息并重建JSON
                const successRateMatch = /"successRate"\s*:\s*(\d+)/.exec(jsonString);
                const totalTasksMatch = /"totalTasks"\s*:\s*(\d+)/.exec(jsonString);
                const todayExecutionsMatch = /"todayExecutions"\s*:\s*(\d+)/.exec(jsonString);
                const recentFailedTasksMatch = /"recentFailedTasks"\s*:\s*(\d+)/.exec(jsonString);
                
                if (successRateMatch && totalTasksMatch && todayExecutionsMatch) {
                    // 创建一个基本对象
                    const reconstructed = {
                        successRate: parseInt(successRateMatch[1]),
                        totalTasks: parseInt(totalTasksMatch[1]),
                        todayExecutions: parseInt(todayExecutionsMatch[1]),
                        recentFailedTasks: recentFailedTasksMatch ? parseInt(recentFailedTasksMatch[1]) : 0,
                        taskTypeDistribution: {},
                        executionStatusDistribution: {}
                    };
                    
                    // 尝试提取状态分布
                    const statusPattern = /"executionStatusDistribution"\s*:\s*{([^}]*)}/;
                    const statusMatch = statusPattern.exec(jsonString);
                    if (statusMatch && statusMatch[1]) {
                        const statusPairs = statusMatch[1].split(',');
                        statusPairs.forEach(pair => {
                            const keyValue = pair.split(':');
                            if (keyValue.length === 2) {
                                let key = keyValue[0].trim().replace(/"/g, '');
                                let value = parseInt(keyValue[1].trim());
                                reconstructed.executionStatusDistribution[key] = value;
                            }
                        });
                    }
                    
                    // 尝试提取任务类型分布
                    const taskPattern = /"taskTypeDistribution"\s*:\s*{([^}]*)}/;
                    const taskMatch = taskPattern.exec(jsonString);
                    if (taskMatch && taskMatch[1]) {
                        const taskPairs = taskMatch[1].split(',');
                        taskPairs.forEach(pair => {
                            const keyValue = pair.split(':');
                            if (keyValue.length === 2) {
                                let key = keyValue[0].trim().replace(/"/g, '');
                                let value = parseInt(keyValue[1].trim());
                                reconstructed.taskTypeDistribution[key] = value;
                            }
                        });
                    }
                    
                    console.log('完全重建的对象:', reconstructed);
                    return reconstructed;
                }
            } catch(e3) {
                console.error('尝试重建JSON对象失败:', e3);
            }
            
            // 安全调用showFeedback函数，确保它存在
            if (showFeedback && typeof window.showFeedback === 'function') {
                window.showFeedback('JSON格式无效，请检查语法', true);
            }
            return null;
        }
    }
}
