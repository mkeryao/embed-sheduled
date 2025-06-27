const API_BASE_URL = '/embed-api'; // Adjust if your context path is different

// 全局调试模式开关
window.debugMode = localStorage.getItem('debugMode') === 'true';

// 提供调试模式切换函数
window.toggleDebugMode = function() {
    window.debugMode = !window.debugMode;
    localStorage.setItem('debugMode', window.debugMode);
    console.log('调试模式: ' + (window.debugMode ? '已启用' : '已禁用'));
    return window.debugMode;
};

// 根据调试模式控制日志输出
window.debugLog = function(...args) {
    if (window.debugMode) {
        console.log(...args);
    }
};

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
    }

    let url = API_BASE_URL + endpoint;
    let ajaxData = null;
    
    // For GET requests, append data as query parameters
    if (method === 'GET' && data) {
        const params = new URLSearchParams();
        for (const key in data) {
            if (data.hasOwnProperty(key)) {
                params.append(key, data[key]);
            }
        }
        url += '?' + params.toString();
    } else if (data) {
        // For other methods, send as JSON body
        ajaxData = JSON.stringify(data);
    }

    $.ajax({
        url: url,
        method: method,
        contentType: method !== 'GET' ? 'application/json' : undefined,
        data: ajaxData,
        headers: {
            'Authorization': 'Bearer' + token
        },
        success: onSuccess,
        error: function(jqXHR, textStatus, errorThrown) {
            if (jqXHR.status === 401 && window.location.pathname !== '/login.html') { // Unauthorized
                // Token might be invalid or expired
                logout(); // Redirect to login
            } else if (onError) {
                onError(jqXHR, textStatus, errorThrown);
            } else {
                // Default error handling
                let message = `Error: ${jqXHR.status} - ${errorThrown}`;
                 if (jqXHR.responseJSON && jqXHR.responseJSON.message) {
                    message = jqXHR.responseJSON.message;
                } else if (jqXHR.responseText) {
                    try {
                        const err = JSON.parse(jqXHR.responseText);
                        if (err.message) message = err.message;
                    } catch(e) { /* ignore parse error */ }
                }
                console.error('API Call Error:', message);
                alert('An error occurred: ' + message);
            }
        }
    });
}

// --- Auth Functions ---
function login(username, password, onSuccess, onError) {
    makeApiCall('POST', '/auth/login', { username, password }, onSuccess, onError);
}

function logout() {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    // Redirect to login page
    if (window.location.pathname !== '/login.html' && window.location.pathname !== '/') {
         window.location.href = 'login.html';
    } else if (window.location.pathname === '/') { // If on index.html, it should redirect
        window.location.href = 'login.html';
    }
}

// --- Utility Functions ---
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
    const publicPages = ['/login.html', '/index.html', '/']; // index.html handles its own redirect

    if (!token && !publicPages.includes(window.location.pathname)) {
        logout(); // Redirect to login if not on a public page and no token
    }
    // If token exists, allow access. Specific pages might do further validation if needed.
}

// Call checkAuth on page load for non-public pages
$(document).ready(function() {
    const publicPages = ['/login.html', '/index.html', '/'];
    if (!publicPages.includes(window.location.pathname)) {
        checkAuth();
    }

    // Global logout link handler
    $('#logout-link').on('click', function(e) {
        e.preventDefault();
        logout();
    });
});

// Helper to display generic success/error messages
function showFeedback(message, isError = false) {
    const feedbackDiv = $('#feedback-message');
    if (feedbackDiv.length) {
        feedbackDiv.text(message)
                   .removeClass(isError ? 'alert-success' : 'alert-danger')
                   .addClass(isError ? 'alert-danger' : 'alert-success')
                   .show();
        setTimeout(() => feedbackDiv.hide(), 5000); // Hide after 5 seconds
    } else {
        alert(message); // Fallback if no feedback div
    }
}
