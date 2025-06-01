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
    }

    $.ajax({
        url: apiUrl,
        method: method,
        contentType: 'application/json',
        data: data ? JSON.stringify(data) : null,
        headers: {
            'Authorization': 'Bearer ' + token
        },
        success: onSuccess,
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
        if (showFeedback) {
            console.error('JSON解析错误:', e, 'JSON字符串:', jsonString);
        }
        
        // 尝试修复常见的JSON格式问题
        let correctedJson = jsonString;
        
        // 1. 修复缺少双引号的键
        correctedJson = correctedJson.replace(/(\s*?{\s*?|\s*?,\s*?)(['"])?([a-zA-Z0-9_]+)(['"])?:/g, '$1"$3":');
        
        // 2. 修复单引号替换为双引号
        correctedJson = correctedJson.replace(/'/g, '"');
        
        try {
            const parsed = JSON.parse(correctedJson);
            if (showFeedback) {
                console.log('JSON已自动修正并解析成功');
                showFeedback('JSON格式已自动修正', false);
            }
            return parsed;
        } catch(e2) {
            if (showFeedback) {
                showFeedback('JSON格式无效，请检查语法', true);
            }
            return null;
        }
    }
}
