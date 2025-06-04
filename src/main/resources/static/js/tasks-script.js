/**
 * tasks_script.js
 * 用于清理和整合tasks.html中的脚本
 * 提供一个正确的脚本区块，无HTML混合
 */

// 全局变量用于存储表单数据
let formData = {
    users: [],
    calendars: [],
    beanTasks: []
};

// 调试辅助函数，用于在控制台查看formData状态
function debugFormData() {
    console.log('当前表单数据状态:', {
        users: formData.users?.length || 0,
        calendars: formData.calendars?.length || 0,
        beanTasks: formData.beanTasks?.length || 0,
        'formData.loaded': componentsStatus.formData
    });
}

// 检查工作流依赖是否正确加载
function checkWorkflowDependencies() {
    let missing = [];
    
    if (typeof cytoscape === 'undefined') missing.push('cytoscape');
    if (typeof dagre === 'undefined') missing.push('dagre');
    if (typeof WorkflowDagViewer === 'undefined') missing.push('WorkflowDagViewer');
    
    if (missing.length > 0) {
        console.warn(`工作流依赖缺失: ${missing.join(', ')}`);
        return false;
    }
    return true;
}

// 全局函数声明（这些函数将通过workflow-global-functions.js提供实现）
// 不要在此处定义函数体，只声明变量
var addWorkflowNodeItem;
var addWorkflowEdgeItem;
var updateNodeSelectors;
var updateEdgeNodeSelectors;
var updateWorkflowNodesJson;
var updateWorkflowEdgesJson;

// 组件加载状态跟踪
const componentsStatus = {
    navbar: false,
    taskForm: false,
    workflowEditor: false,
    tasksData: false,
    formData: false
};

// 处理工作流DAG标签页点击事件
$(document).on('click', '#workflow-dag-tab', function() {
    console.log('工作流DAG标签页被点击');
    
    // 显示加载中状态
    $('#workflow-dag-content').html(`
        <div class="alert alert-info mt-3">
            <i class="fas fa-spinner fa-spin"></i> 
            正在检查工作流组件状态...
        </div>`);
    
    // 使用安全加载工作流依赖的方法
    if (typeof window.ensureWorkflowDependencies === 'function') {
        // 先确保依赖加载完成
        console.log('使用安全方式加载工作流依赖...');
        window.ensureWorkflowDependencies(function() {
            // 依赖加载完成后，初始化DAG图
            console.log('工作流依赖加载完成，准备初始化DAG图');
            
            // 延迟执行，确保DOM已更新
            setTimeout(function() {
                if (typeof WorkflowInitializer !== 'undefined' && 
                    typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
                    console.log('初始化/刷新工作流DAG图...');
                    WorkflowInitializer.initOrRefreshWorkflowDAG();
                } else {
                    console.error('WorkflowInitializer不可用，无法初始化DAG图');
                    $('#workflow-dag-content').html(`
                        <div class="alert alert-danger mt-3">
                            <i class="fas fa-exclamation-circle"></i> 
                            工作流初始化器不可用，无法显示DAG图。
                            <button class="btn btn-sm btn-outline-primary" id="try-init-workflow">
                                尝试手动初始化
                            </button>
                        </div>`);
                        
                    // 绑定手动初始化按钮
                    $('#try-init-workflow').on('click', function() {
                        location.reload();
                    });
                }
            }, 300);
        });
    } else if (checkWorkflowDependencies()) {
        // 退回到原始方法
        setTimeout(function() {
            if (typeof WorkflowInitializer !== 'undefined' && 
                typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
                console.log('初始化/刷新工作流DAG图...');
                WorkflowInitializer.initOrRefreshWorkflowDAG();
            } else {
                console.error('WorkflowInitializer不可用，无法初始化DAG图');
            }
        }, 300);
    } else {
        console.error('工作流依赖未完全加载，无法显示DAG图');
        $('#workflow-dag-content').html(`
            <div class="alert alert-warning mt-3">
                <i class="fas fa-exclamation-triangle"></i> 
                工作流可视化组件未完全加载。
                <button class="btn btn-sm btn-outline-primary reload-workflow-deps-btn">
                    尝试重新加载
                </button>
            </div>`);
            
        // 绑定重新加载按钮
        $('.reload-workflow-deps-btn').on('click', function() {
            $('#workflow-dag-content').html(`
                <div class="alert alert-info mt-3">
                    <i class="fas fa-spinner fa-spin"></i> 
                    正在重新加载组件...
                </div>`);
                
            // 手动加载脚本
            const loadScript = function(src) {
                return new Promise((resolve, reject) => {
                    const script = document.createElement('script');
                    script.src = src;
                    script.onload = resolve;
                    script.onerror = reject;
                    document.head.appendChild(script);
                });
            };
            
            // 按顺序加载必要的脚本
            loadScript('libs/cytoscape/dagre.min.js')
                .then(() => loadScript('libs/cytoscape/layout/cytoscape-dagre.js'))
                .then(() => {
                    // 加载成功，刷新DAG图
                    if (typeof WorkflowInitializer !== 'undefined' && 
                        typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
                        WorkflowInitializer.initOrRefreshWorkflowDAG();
                    }
                })
                .catch((error) => {
                    console.error('加载脚本失败:', error);
                    $('#workflow-dag-content').html(`
                        <div class="alert alert-danger mt-3">
                            <i class="fas fa-times-circle"></i> 
                            加载工作流组件失败，请刷新页面重试。
                        </div>`);
                });
        });
    }
});

// 显示反馈信息的函数
function showFeedback(message, isError) {
    const alertClass = isError ? 'alert-danger' : 'alert-success';
    const icon = isError ? '<i class="fas fa-exclamation-circle"></i>' : '<i class="fas fa-check-circle"></i>';
    
    // 创建并显示反馈信息
    const alertDiv = $(`<div class="alert ${alertClass} alert-dismissible fade show" role="alert">
        ${icon} ${message}
        <button type="button" class="close" data-dismiss="alert" aria-label="Close">
            <span aria-hidden="true">&times;</span>
        </button>
    </div>`);
    
    // 添加到容器顶部
    $('.container').prepend(alertDiv);
    
    // 5秒后自动隐藏
    setTimeout(() => {
        alertDiv.alert('close');
    }, 5000);
}

// 显示组件错误的函数
function showComponentError(componentName, error) {
    console.error(`${componentName}加载失败:`, error);
    const errorHtml = `
    <div class="component-error">
        <h5>${componentName}加载错误</h5>
        <p>${error.message || error}</p>
        <button class="btn btn-sm btn-danger retry-btn">重试</button>
    </div>`;
    
    // 替换加载指示器
    switch(componentName) {
        case '导航栏':
            $("#navbar-container").html(errorHtml);
            break;
        case '任务表单':
            $("#task-form-container").html(errorHtml);
            break;
        case '工作流编辑器':
            $("#workflow-editor-component").html(errorHtml);
            break;
        case '任务数据':
            $("#tasks-loading").replaceWith(errorHtml);
            break;
    }
    
    // 绑定重试按钮事件
    $('.retry-btn').on('click', function() {
        // 不要直接刷新页面，可能导致无限循环
        console.log('重新加载组件...');
        // 重新加载特定组件，而非整个页面
        const componentType = $(this).data('component-type');
        reloadComponent(componentType || 'unknown');
    });
}

// 重新加载特定组件而不是整个页面
function reloadComponent(componentType) {
    console.log(`重新加载组件: ${componentType}`);
    switch(componentType) {
        case 'navbar':
            $("#navbar-container").html(`
                <div class="component-loader">
                    <i class="fas fa-spinner"></i> 正在重新加载导航栏...
                </div>
            `);
            $("#navbar-container").load("_navbar.html");
            break;
        case 'taskForm':
            $("#task-form-container").html(`
                <div class="component-loader">
                    <i class="fas fa-spinner"></i> 正在重新加载表单组件...
                </div>
            `);
            $("#task-form-container").load("_task-form.html");
            break;
        case 'workflowEditor':
            $("#workflow-editor-container").html(`
                <div class="component-loader">
                    <i class="fas fa-spinner"></i> 正在重新加载工作流编辑器...
                </div>
            `);
            $("#workflow-editor-container").load("_workflow-editor.html");
            break;
        case 'tasksData':
            loadTasks();
            break;
        default:
            // 加载必要的核心组件
            console.log('重新加载核心组件...');
            loadTasks();
            break;
    }
}

// 初始化页面加载逻辑
$(document).ready(function() {
    console.log('任务管理页面初始化...');
    
    // 加载导航栏 - 前后端分离架构，使用正确路径
    $("#navbar-container").load("_navbar.html", function(response, status, xhr) {
        if (status === "error") {
            showComponentError('导航栏', xhr.statusText);
            console.error('导航栏加载错误:', xhr.status, xhr.statusText);
            return;
        }
        
        componentsStatus.navbar = true;
        console.log("导航栏加载完成");
        
        // 处理用户登录信息
        const currentUsername = localStorage.getItem('username');
        if (currentUsername) {
            $('#loggedInUser').text(currentUsername);
        }
        
        // 重新绑定登出事件
        $('#logout-link').on('click', function(e) {
            e.preventDefault();
            logout();
        });
    });
    
    // 加载任务表单模态框 - 前后端分离架构，使用正确路径
    $("#task-form-container").load("_task-form.html", function(response, status, xhr) {
        if (status === "error") {
            showComponentError('任务表单', xhr.statusText);
            console.error('任务表单加载错误:', xhr.status, xhr.statusText);
            return;
        }
        
        componentsStatus.taskForm = true;
        console.log("任务表单模态框加载完成");
        
        // 在表单中加载工作流编辑器组件
        // 先加载页面级组件
        $("#workflow-editor-component").removeClass('hidden-component');
        // 然后加载表单内部组件
        $("#workflow-editor-container").load("_workflow-editor.html", function(response, status, xhr) {
            if (status === "error") {
                showComponentError('工作流编辑器', xhr.statusText);
                console.error('工作流编辑器加载错误:', xhr.status, xhr.statusText);
                return;
            }
            
            componentsStatus.workflowEditor = true;
            console.log("工作流编辑器组件加载完成");
            
            try {
                // 初始化工作流编辑器
                initWorkflowEditor();
                
                // 确保工作流编辑器在工作流任务类型下可见
                if ($('#taskType').val() === '10') {
                    $('#workflowTaskFields').show();
                }
            } catch (error) {
                console.error("工作流编辑器初始化失败:", error);
                showFeedback("工作流编辑器初始化失败: " + error.message, true);
            }
        });
        
        // 绑定表单相关事件处理器
        try {
            bindFormEventHandlers();
        } catch (error) {
            console.error("表单事件处理器绑定失败:", error);
            showFeedback("表单事件处理器绑定失败: " + error.message, true);
        }
    });
    
    // 加载任务和表单数据
    loadTasks();
    loadFormData();
    
    // 绑定页面级事件处理器
    try {
        bindEventHandlers();
    } catch (error) {
        console.error("事件处理器绑定失败:", error);
        showFeedback("事件处理器绑定失败: " + error.message, true);
    }
});

// 获取任务类型字符串
function getTaskTypeString(typeInt) {
    switch (typeInt) {
        case 0: return "Bean任务";
        case 2: return "HTTP任务";
        case 4: return "Shell脚本";
        case 10: return "工作流";
        default: return `未知 (${typeInt})`;
    }
}

// 加载任务列表
function loadTasks() {
    // 显示加载指示器
    $('#tasks-loading').show();
    $('.tasks-table').hide();
    
    // 使用统一的API调用方式，直接使用/api前缀避免路径问题
    makeApiCall('GET', '/api/tasks', null,
        function (tasks) {
            // 请求成功
            componentsStatus.tasksData = true;
            
            const tableBody = $('#tasks-table-body');
            tableBody.empty();
            
            // 如果没有任务，显示空状态
            if (!tasks || tasks.length === 0) {
                tableBody.append(`
                    <tr>
                        <td colspan="9" class="text-center py-4">
                            <i class="fas fa-inbox fa-2x mb-3 text-muted"></i>
                            <p>暂无任务数据</p>
                        </td>
                    </tr>
                `);
            } else {
                // 渲染任务列表
                tasks.forEach(function (task) {
                    // 兼容处理isActive字段，支持task.isActive和task.active两种情况
                    const isActive = (task.isActive !== undefined) ? task.isActive : task.active;
                    const status = isActive 
                        ? '<span class="badge badge-success"><i class="fas fa-check-circle"></i> 激活</span>' 
                        : '<span class="badge badge-secondary"><i class="fas fa-times-circle"></i> 未激活</span>';
                    
                    // 使用新的格式化函数构建有效时间范围显示
                    let validTimeRange = formatDateTimeRange(task.startDate, task.endDate);
                    
                    // 为有开始和结束日期的任务添加更明显的样式
                    if (task.startDate && task.endDate) {
                        validTimeRange = `<span class="start-date">从: ${formatDateTime(task.startDate, false)}</span>` +
                            `<span class="end-date">至: ${formatDateTime(task.endDate, false)}</span>`;
                    } else if (task.startDate) {
                        validTimeRange = `<span class="start-date">从: ${formatDateTime(task.startDate, false)}</span>`;
                    } else if (task.endDate) {
                        validTimeRange = `<span class="end-date">至: ${formatDateTime(task.endDate, false)}</span>`;
                    }

                    const row = `<tr>
                        <td>${task.taskId}</td>
                        <td>${task.taskGroup || ''}</td>
                        <td>${task.taskName || ''}</td>
                        <td>${task.cronExpression || ''}</td>
                        <td>${getTaskTypeString(task.taskType)}</td>
                        <td><small class="valid-time-range">${validTimeRange}</small></td>
                        <td>${status}</td>
                        <td>${task.executionMode || 'BROADCAST'}</td>
                        <td class="action-buttons">
                            <button class="btn btn-sm btn-info edit-btn" data-id="${task.taskId}"><i class="fas fa-edit"></i> 编辑</button>
                            <button class="btn btn-sm btn-danger delete-btn" data-id="${task.taskId}"><i class="fas fa-trash"></i> 删除</button>
                            <button class="btn btn-sm btn-secondary trigger-btn" data-id="${task.taskId}"><i class="fas fa-play"></i> 触发</button>
                            <button class="btn btn-sm btn-info view-logs-btn" data-id="${task.taskId}" data-task-name="${task.taskName}"><i class="fas fa-clipboard-list"></i> 查看日志</button>
                            ${isActive
                                ? `<button class="btn btn-sm btn-warning disable-btn" data-id="${task.taskId}"><i class="fas fa-ban"></i> 禁用</button>`
                                : `<button class="btn btn-sm btn-success enable-btn" data-id="${task.taskId}"><i class="fas fa-check"></i> 启用</button>`
                            }
                        </td>
                    </tr>`;
                    tableBody.append(row);
                });
            }
            
            // 隐藏加载指示器，显示表格
            $('#tasks-loading').hide();
            $('.tasks-table').show();
        },
        function (jqXHR) {
            // 请求失败
            componentsStatus.tasksData = false;
            
            // 替换加载指示器为错误信息
            $('#tasks-loading').replaceWith(`
                <div class="component-error">
                    <h5>加载任务数据失败</h5>
                    <p>${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}</p>
                    <button class="btn btn-sm btn-danger retry-tasks-btn">重试</button>
                </div>
            `);
            
            // 绑定重试按钮事件
            $('.retry-tasks-btn').on('click', function() {
                // 移除错误信息
                $('.component-error').replaceWith(`
                    <div id="tasks-loading" class="component-loader">
                        <i class="fas fa-spinner"></i> 正在重新加载任务数据...
                    </div>
                `);
                // 重新加载任务
                loadTasks();
            });
        }
    );
}

// 加载表单数据（用户、日历等）
function loadFormData() {
    // 显示加载状态（可以在模态框中添加一个加载指示器）
    const formDataLoader = $(`<div id="form-data-loading" class="component-loader">
        <i class="fas fa-spinner"></i> 正在加载表单数据...
    </div>`);
    
    // 只有在模态框已经加载的情况下才添加加载指示器
    if (componentsStatus.taskForm) {
        $('#taskFormModal .modal-body').prepend(formDataLoader);
    }
    
    // 确保使用正确的API路径，表单数据路径为/api/tasks/form-data
    makeApiCall('GET', '/api/tasks/form-data', null,
        function (data) {
            // 请求成功
            componentsStatus.formData = true;
            console.log('表单数据加载成功:', data);
            
            // 更新全局表单数据
            formData = data;
            
            // 移除加载指示器
            $('#form-data-loading').remove();
            
            // 只有在模态框已经加载的情况下才填充选择器
            if (componentsStatus.taskForm) {
                try {
                    // 填充用户选择器
                    const successUserSelect = $('#notifySuccessUserIds');
                    const failedUserSelect = $('#notifyFailedUserIds');
                    
                    if (formData.users && formData.users.length) {
                        successUserSelect.empty();
                        failedUserSelect.empty();
                        
                        formData.users.forEach(user => {
                            successUserSelect.append(
                                `<option value="${user.userId}">${user.username} (${user.email || '无邮箱'})</option>`
                            );
                            failedUserSelect.append(
                                `<option value="${user.userId}">${user.username} (${user.email || '无邮箱'})</option>`
                            );
                        });
                    }
                    
                    // 填充日历选择器
                    const calendarSelect = $('#taskCalendarGroup');
                    
                    if (formData.calendars && formData.calendars.length) {
                        calendarSelect.empty().append('<option value="">-- 选择日历 --</option>');
                        
                        formData.calendars.forEach(calendar => {
                            calendarSelect.append(
                                `<option value="${calendar.calendarId}">${calendar.calendarName}</option>`
                            );
                        });
                    }
                    
                    // 如果有bean任务数据，可以填充相关选择器
                    if (formData.beanTasks && formData.beanTasks.length) {
                        console.log(`加载了${formData.beanTasks.length}个Bean任务`);
                        
                        // 将beanTasks分配给window对象，使其可用于工作流节点选择器
                        window.formData = window.formData || {};
                        window.formData.beanTasks = formData.beanTasks;
                        
                        // 如果工作流编辑器已加载并有现有节点，刷新它们的任务选择器
                        if (componentsStatus.workflowEditor) {
                            $('.workflow-node-item').each(function() {
                                const taskSelect = $(this).find('.node-task-select');
                                const currentValue = taskSelect.val();
                                
                                // 清空并重新填充选择器
                                taskSelect.empty().append('<option value="">-- 选择任务 --</option>');
                                
                                formData.beanTasks.forEach(function(task) {
                                    const selected = task.task_id.toString() === currentValue ? 'selected' : '';
                                    taskSelect.append(`<option value="${task.task_id}" ${selected}>${task.task_name} (${task.bean_name})</option>`);
                                });
                            });
                        }
                    }
                } catch (error) {
                    console.error('填充表单数据时出错:', error);
                    showFeedback('填充表单数据时出错: ' + error.message, true);
                }
            }
        },
        function (jqXHR) {
            // 请求失败
            componentsStatus.formData = false;
            
            // 移除加载指示器
            $('#form-data-loading').remove();
            
            // 显示错误信息
            const errorMessage = jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText;
            showFeedback('加载表单数据出错: ' + errorMessage, true);
            
            // 如果模态框已加载，在模态框中显示错误信息
            if (componentsStatus.taskForm) {
                $('#taskFormModal .modal-body').prepend(`
                    <div class="alert alert-danger">
                        <i class="fas fa-exclamation-triangle"></i> 无法加载表单数据，部分功能可能不可用。
                        <button class="btn btn-sm btn-danger ml-2 reload-form-data-btn">重试</button>
                    </div>
                `);
                
                // 绑定重试按钮事件
                $('.reload-form-data-btn').on('click', function() {
                    $(this).closest('.alert').remove();
                    loadFormData();
                });
            }
        }
    );
}

// 初始化工作流编辑器
function initWorkflowEditor() {
    console.log('初始化工作流编辑器...');
    
    // 检查工作流编辑器组件是否已加载
    if (!componentsStatus.workflowEditor) {
        console.error('工作流编辑器组件尚未加载，无法初始化');
        return;
    }
    
    // 确保全局函数已加载
    const requiredFunctions = [
        'addWorkflowNodeItem', 'addWorkflowEdgeItem', 
        'updateNodeSelectors', 'updateEdgeNodeSelectors',
        'updateWorkflowNodesJson', 'updateWorkflowEdgesJson'
    ];
    
    // 验证必要的全局函数是否已定义
    const missingFunctions = requiredFunctions.filter(fn => typeof window[fn] !== 'function');
    
    if (missingFunctions.length > 0) {
        console.error(`以下工作流全局函数未定义: ${missingFunctions.join(', ')}`);
        showFeedback(`工作流编辑器初始化失败: 缺少必要的函数定义`, true);
        return;
    }
    
    // 添加节点按钮事件
    $('#add-workflow-node-btn').on('click', function() {
        addWorkflowNodeItem({});
    });
    
    // 添加边按钮事件
    $('#add-workflow-edge-btn').on('click', function() {
        addWorkflowEdgeItem({});
    });
    
    // 初始化工作流节点和边的JSON字段
    if ($('#workflowNodesJson').val() === '') {
        $('#workflowNodesJson').val('[]');
    }
    if ($('#workflowEdgesJson').val() === '') {
        $('#workflowEdgesJson').val('[]');
    }
    
    console.log('工作流编辑器初始化完成');
}

// 绑定表单相关事件处理器
function bindFormEventHandlers() {
    console.log('绑定表单事件处理器...');
    
    // 任务类型变更事件
    $('#taskType').on('change', function() {
        const taskType = parseInt($(this).val());
        $('.type-specific-fields').hide();
        
        switch (taskType) {
            case 0: // Bean任务
                $('#beanTaskFields').show();
                break;
            case 2: // HTTP任务
                $('#httpTaskFields').show();
                break;
            case 4: // Shell脚本
                $('#shellTaskFields').show();
                break;
            case 10: // 工作流
                $('#workflowTaskFields').show();
                break;
        }
    });
    
    // 表单提交事件
    $('#task-form').on('submit', function(e) {
        e.preventDefault();
        // 表单提交逻辑...
        saveTask();
    });
}

// 绑定页面级事件处理器
function bindEventHandlers() {
    console.log('绑定页面事件处理器...');
    
    // 编辑任务按钮事件
    $(document).on('click', '.edit-btn', function() {
        const taskId = $(this).data('id');
        editTask(taskId);
    });
    
    // 删除任务按钮事件
    $(document).on('click', '.delete-btn', function() {
        const taskId = $(this).data('id');
        deleteTask(taskId);
    });
    
    // 触发任务按钮事件
    $(document).on('click', '.trigger-btn', function() {
        const taskId = $(this).data('id');
        triggerTask(taskId);
    });
    
    // 查看日志按钮事件
    $(document).on('click', '.view-logs-btn', function() {
        const taskId = $(this).data('id');
        const taskName = $(this).data('task-name');
        viewTaskLogs(taskId, taskName);
    });
    
    // 启用任务按钮事件
    $(document).on('click', '.enable-btn', function() {
        const taskId = $(this).data('id');
        enableTask(taskId);
    });
    
    // 禁用任务按钮事件
    $(document).on('click', '.disable-btn', function() {
        const taskId = $(this).data('id');
        disableTask(taskId);
    });
    
    // 查看日志按钮事件
    $(document).on('click', '.view-logs-btn', function() {
        const taskId = $(this).data('id');
        const taskName = $(this).data('task-name');
        viewTaskLogs(taskId, taskName);
    });
    
    // 禁用任务按钮事件
    $(document).on('click', '.disable-btn', function() {
        const taskId = $(this).data('id');
        updateTaskStatus(taskId, false);
    });
    
    // 启用任务按钮事件
    $(document).on('click', '.enable-btn', function() {
        const taskId = $(this).data('id');
        updateTaskStatus(taskId, true);
    });
    
    // 添加任务按钮事件
    $('#addTaskBtn').on('click', function() {
        resetTaskForm();
    });
}

// 更新任务状态
function updateTaskStatus(taskId, active) {
    makeApiCall('PUT', `/tasks/${taskId}/status`, { active: active },
        function() {
            showFeedback(`任务 ${active ? '启用' : '禁用'} 成功`, false);
            loadTasks();
        },
        function(jqXHR) {
            showFeedback(`更新任务状态失败: ${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}`, true);
        }
    );
}

// 保存任务函数
function saveTask() {
    console.log('保存任务...');
    
    // 显示保存中状态
    $('#task-form button[type="submit"]').prop('disabled', true)
        .html('<i class="fas fa-spinner fa-spin"></i> 保存中...');
    
    try {
        // 收集表单数据
        const formData = {
            taskId: $('#taskId').val() || null,
            taskName: $('#taskName').val(),
            taskGroup: $('#taskGroup').val(),
            cronExpression: $('#cronExpression').val(),
            description: $('#description').val(),
            executionMode: $('#executionMode').val(),
            taskType: parseInt($('#taskType').val()),
            startDate: $('#startDate').val() || null,
            endDate: $('#endDate').val() || null,
            retryTimes: parseInt($('#retryTimes').val()) || 0,
            retryInterval: parseInt($('#retryInterval').val()) || 0
        };
        
        // 根据任务类型添加特定字段
        switch (formData.taskType) {
            case 0: // Bean任务
                formData.beanName = $('#beanName').val();
                formData.methodName = $('#methodName').val();
                formData.methodParams = $('#methodParams').val();
                break;
            
            case 2: // HTTP任务
                formData.httpMethod = $('#httpMethod').val();
                formData.httpUrl = $('#httpUrl').val();
                formData.httpHeaders = $('#httpHeaders').val();
                formData.httpBody = $('#httpBody').val();
                break;
            
            case 4: // Shell脚本
                formData.shellScript = $('#shellScript').val();
                break;
            
            case 10: // 工作流
                // 获取工作流节点和边数据
                formData.workflowNodesJson = $('#workflowNodesJson').val();
                formData.workflowEdgesJson = $('#workflowEdgesJson').val();
                break;
        }
        
        // 通知设置
        formData.notifyOnSuccess = $('#notifyOnSuccess').prop('checked');
        formData.notifyOnFailure = $('#notifyOnFailure').prop('checked');
        
        // 转换为数组
        if ($('#notifySuccessUserIds').val()) {
            formData.notifySuccessUserIds = $('#notifySuccessUserIds').val();
        }
        if ($('#notifyFailedUserIds').val()) {
            formData.notifyFailedUserIds = $('#notifyFailedUserIds').val();
        }
        
        // 日历组
        if ($('#taskCalendarGroup').val()) {
            formData.calendarId = $('#taskCalendarGroup').val();
        }
        
        // 验证表单数据
        if (!validateTaskForm(formData)) {
            return;
        }
        
        // 发送API请求
        const isUpdate = formData.taskId ? true : false;
        const url = isUpdate ? `/tasks/${formData.taskId}` : '/tasks';
        const method = isUpdate ? 'PUT' : 'POST';
        
        makeApiCall(method, url, formData,
            function (data) {
                // 成功处理
                showFeedback(`任务已${isUpdate ? '更新' : '创建'}成功！`, false);
                
                // 关闭模态框
                $('#taskFormModal').modal('hide');
                
                // 重新加载任务列表
                loadTasks();
                
                // 重置表单
                resetTaskForm();
            },
            function (jqXHR) {
                // 错误处理
                const errorMsg = jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText;
                showFeedback(`保存任务失败: ${errorMsg}`, true);
                
                // 在表单上方显示错误
                $('#task-form').prepend(`
                    <div class="alert alert-danger">
                        <i class="fas fa-exclamation-circle"></i> 保存失败: ${errorMsg}
                    </div>
                `);
                
                // 3秒后移除错误提示
                setTimeout(() => {
                    $('#task-form .alert').fadeOut(function() {
                        $(this).remove();
                    });
                }, 3000);
            }
        );
    } catch (error) {
        console.error('保存任务时出错:', error);
        showFeedback(`保存任务时出错: ${error.message}`, true);
    } finally {
        // 恢复提交按钮
        $('#task-form button[type="submit"]').prop('disabled', false).html('保存');
    }
}

// 验证任务表单
function validateTaskForm(formData) {
    let isValid = true;
    let errorMessages = [];
    
    // 基本字段验证
    if (!formData.taskName) {
        errorMessages.push('任务名称不能为空');
        isValid = false;
    }
    
    if (!formData.cronExpression) {
        errorMessages.push('CRON表达式不能为空');
        isValid = false;
    }
    
    // 根据任务类型验证特定字段
    switch (formData.taskType) {
        case 0: // Bean任务
            if (!formData.beanName) {
                errorMessages.push('Bean名称不能为空');
                isValid = false;
            }
            if (!formData.methodName) {
                errorMessages.push('方法名称不能为空');
                isValid = false;
            }
            break;
        
        case 2: // HTTP任务
            if (!formData.httpUrl) {
                errorMessages.push('HTTP URL不能为空');
                isValid = false;
            }
            break;
        
        case 4: // Shell脚本
            if (!formData.shellScript) {
                errorMessages.push('Shell脚本不能为空');
                isValid = false;
            }
            break;
        
        case 10: // 工作流
            if (!formData.workflowNodesJson || formData.workflowNodesJson === '[]') {
                errorMessages.push('工作流必须至少包含一个节点');
                isValid = false;
            }
            break;
    }
    
    // 显示验证错误
    if (!isValid) {
        // 清除旧的验证错误
        $('#task-form .validation-errors').remove();
        
        // 添加新的验证错误
        const errorHtml = `
            <div class="alert alert-danger validation-errors">
                <i class="fas fa-exclamation-circle"></i> 请修正以下错误:
                <ul>
                    ${errorMessages.map(msg => `<li>${msg}</li>`).join('')}
                </ul>
            </div>
        `;
        
        $('#task-form').prepend(errorHtml);
    }
    
    return isValid;
}

// 重置任务表单
function resetTaskForm() {
    // 清除表单字段
    $('#task-form')[0].reset();
    
    // 清除隐藏字段
    $('#taskId').val('');
    
    // 隐藏特定类型字段区域
    $('.type-specific-fields').hide();
    
    // 清除工作流编辑器内容
    $('.workflow-nodes-container').empty();
    $('.workflow-edges-container').empty();
    $('#workflowNodesJson').val('[]');
    $('#workflowEdgesJson').val('[]');
    
    // 移除验证错误提示
    $('#task-form .validation-errors').remove();
}

// 编辑任务
function editTask(taskId) {
    console.log(`编辑任务 ID: ${taskId}`);
    
    // 显示加载中状态
    $('#taskFormModalLabel').html('<i class="fas fa-spinner fa-spin"></i> 加载任务数据...');
    
    makeApiCall('GET', `/tasks/${taskId}`, null,
        function (task) {
            // 更新模态框标题
            $('#taskFormModalLabel').text('编辑任务');
            
            // 填充表单字段
            $('#taskId').val(task.taskId);
            $('#taskName').val(task.taskName);
            $('#taskGroup').val(task.taskGroup);
            $('#cronExpression').val(task.cronExpression);
            $('#description').val(task.description);
            $('#executionMode').val(task.executionMode);
            $('#taskType').val(task.taskType).trigger('change');
            
            if (task.startDate) {
                $('#startDate').val(task.startDate.substring(0, 16));
            }
            if (task.endDate) {
                $('#endDate').val(task.endDate.substring(0, 16));
            }
            
            // 设置重试配置
            $('#retryTimes').val(task.retryTimes || 0);
            $('#retryInterval').val(task.retryInterval || 0);
            
            // 设置通知选项
            $('#notifyOnSuccess').prop('checked', task.notifyOnSuccess);
            $('#notifyOnFailure').prop('checked', task.notifyOnFailure);
            
            if (task.notifySuccessUserIds && task.notifySuccessUserIds.length) {
                $('#notifySuccessUserIds').val(task.notifySuccessUserIds);
            }
            if (task.notifyFailedUserIds && task.notifyFailedUserIds.length) {
                $('#notifyFailedUserIds').val(task.notifyFailedUserIds);
            }
            
            // 设置日历组
            if (task.calendarId) {
                $('#taskCalendarGroup').val(task.calendarId);
            }
            
            // 根据任务类型设置特定字段
            switch (parseInt(task.taskType)) {
                case 0: // Bean任务
                    $('#beanName').val(task.beanName);
                    $('#methodName').val(task.methodName);
                    $('#methodParams').val(task.methodParams);
                    break;
                
                case 2: // HTTP任务
                    $('#httpMethod').val(task.httpMethod);
                    $('#httpUrl').val(task.httpUrl);
                    $('#httpHeaders').val(task.httpHeaders);
                    $('#httpBody').val(task.httpBody);
                    break;
                
                case 4: // Shell脚本
                    $('#shellScript').val(task.shellScript);
                    break;
                
                case 10: // 工作流
                    console.log('加载工作流配置...');
                    if (task.workflowNodesJson) {
                        $('#workflowNodesJson').val(task.workflowNodesJson);
                        
                        try {
                            // 解析并可视化工作流节点
                            const nodes = JSON.parse(task.workflowNodesJson);
                            console.log(`加载了${nodes.length}个工作流节点`);
                            $('.workflow-nodes-container').empty();
                            
                            nodes.forEach(node => {
                                if (typeof addWorkflowNodeItem === 'function') {
                                    addWorkflowNodeItem(node);
                                } else {
                                    console.error('addWorkflowNodeItem函数未定义，无法添加工作流节点');
                                }
                            });
                        } catch (e) {
                            console.error('解析工作流节点失败:', e);
                        }
                    }
                    
                    if (task.workflowEdgesJson) {
                        $('#workflowEdgesJson').val(task.workflowEdgesJson);
                        
                        try {
                            // 解析并可视化工作流边
                            const edges = JSON.parse(task.workflowEdgesJson);
                            console.log(`加载了${edges.length}个工作流边`);
                            $('.workflow-edges-container').empty();
                            
                            edges.forEach(edge => {
                                if (typeof addWorkflowEdgeItem === 'function') {
                                    addWorkflowEdgeItem(edge);
                                } else {
                                    console.error('addWorkflowEdgeItem函数未定义，无法添加工作流边');
                                }
                            });
                        } catch (e) {
                            console.error('解析工作流边失败:', e);
                        }
                    }
                    
                    // 添加一个延迟初始化工作流DAG图的调用
                    setTimeout(function() {
                        if (typeof WorkflowInitializer !== 'undefined' && 
                            typeof WorkflowInitializer.initOrRefreshWorkflowDAG === 'function') {
                            console.log('初始化工作流DAG图...');
                            WorkflowInitializer.initOrRefreshWorkflowDAG();
                        } else {
                            console.error('WorkflowInitializer.initOrRefreshWorkflowDAG函数不可用');
                        }
                    }, 500); // 给DOM一些时间来更新
                    
                    break;
            }
            
            // 显示模态框
            $('#taskFormModal').modal('show');
        },
        function (jqXHR) {
            // 失败处理
            showFeedback(`加载任务数据失败: ${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}`, true);
        }
    );
}

// 删除任务
function deleteTask(taskId) {
    console.log(`删除任务 ID: ${taskId}`);
    
    if (!confirm('确定要删除这个任务吗？此操作无法撤销。')) {
        return;
    }
    
    makeApiCall('DELETE', `/tasks/${taskId}`, null,
        function (data) {
            // 成功删除
            showFeedback('任务已成功删除！', false);
            
            // 重新加载任务列表
            loadTasks();
        },
        function (jqXHR) {
            // 删除失败
            showFeedback(`删除任务失败: ${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}`, true);
        }
    );
}

// 触发任务执行
function triggerTask(taskId) {
    console.log(`触发任务 ID: ${taskId}`);
    
    if (!confirm('确定要立即触发执行这个任务吗？')) {
        return;
    }
    
    const triggerBtn = $(`.trigger-btn[data-id="${taskId}"]`);
    triggerBtn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 触发中...');
    
    makeApiCall('POST', `/tasks/${taskId}/trigger`, null,
        function (data) {
            // 触发成功
            showFeedback('任务触发成功！', false);
            
            // 恢复按钮状态
            triggerBtn.prop('disabled', false).html('<i class="fas fa-play"></i> 触发');
        },
        function (jqXHR) {
            // 触发失败
            showFeedback(`任务触发失败: ${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}`, true);
            
            // 恢复按钮状态
            triggerBtn.prop('disabled', false).html('<i class="fas fa-play"></i> 触发');
        }
    );
}

// 启用任务
function enableTask(taskId) {
    console.log(`启用任务 ID: ${taskId}`);
    
    const enableBtn = $(`.enable-btn[data-id="${taskId}"]`);
    enableBtn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 处理中...');
    
    makeApiCall('POST', `/tasks/${taskId}/enable`, null,
        function (data) {
            // 启用成功
            showFeedback('任务已成功启用！', false);
            
            // 重新加载任务列表
            loadTasks();
        },
        function (jqXHR) {
            // 启用失败
            showFeedback(`启用任务失败: ${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}`, true);
            
            // 恢复按钮状态
            enableBtn.prop('disabled', false).html('<i class="fas fa-check"></i> 启用');
        }
    );
}

// 禁用任务
function disableTask(taskId) {
    console.log(`禁用任务 ID: ${taskId}`);
    
    const disableBtn = $(`.disable-btn[data-id="${taskId}"]`);
    disableBtn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 处理中...');
    
    makeApiCall('POST', `/tasks/${taskId}/disable`, null,
        function (data) {
            // 禁用成功
            showFeedback('任务已成功禁用！', false);
            
            // 重新加载任务列表
            loadTasks();
        },
        function (jqXHR) {
            // 禁用失败
            showFeedback(`禁用任务失败: ${jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText}`, true);
            
            // 恢复按钮状态
            disableBtn.prop('disabled', false).html('<i class="fas fa-ban"></i> 禁用');
        }
    );
}

// 查看任务日志
function viewTaskLogs(taskId, taskName) {
    console.log(`查看任务日志 ID: ${taskId}, 名称: ${taskName}`);
    
    // 将任务ID和名称存储到本地存储中
    localStorage.setItem('viewTaskId', taskId);
    localStorage.setItem('viewTaskName', taskName);
    
    // 重定向到日志页面
    window.location.href = 'logs.html?taskId=' + taskId;
}

// 通用API调用函数 - 使用main.js中的统一版本
// 在前后端分离架构中，API调用逻辑应该统一
// 此处不再定义makeApiCall函数，使用main.js中的版本

// 格式化日期时间范围
function formatDateTimeRange(startDate, endDate) {
    if (startDate && endDate) {
        return `${formatDateTime(startDate, true)} 至 ${formatDateTime(endDate, true)}`;
    } else if (startDate) {
        return `从 ${formatDateTime(startDate, true)} 开始`;
    } else if (endDate) {
        return `截至 ${formatDateTime(endDate, true)}`;
    }
    return '无限制';
}

// 格式化日期时间
function formatDateTime(dateTimeString, includeTime) {
    if (!dateTimeString) return '';
    
    const dt = new Date(dateTimeString);
    
    const year = dt.getFullYear();
    const month = String(dt.getMonth() + 1).padStart(2, '0');
    const day = String(dt.getDate()).padStart(2, '0');
    const hours = String(dt.getHours()).padStart(2, '0');
    const minutes = String(dt.getMinutes()).padStart(2, '0');
    
    return includeTime
        ? `${year}-${month}-${day} ${hours}:${minutes}`
        : `${year}-${month}-${day}`;
}

// 用户登出
function logout() {
    // 清除本地存储的用户信息
    localStorage.removeItem('username');
    localStorage.removeItem('token');
    
    // 重定向到登录页面
    window.location.href = 'login.html';
}
