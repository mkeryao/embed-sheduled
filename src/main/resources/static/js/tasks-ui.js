/**
 * tasks-ui.js
 * 任务管理UI交互功能，包括事件绑定、表单处理等
 */

// 避免重复初始化
if (typeof window.tasksUiInitialized === 'undefined') {
    console.log('初始化tasks UI脚本...');
    window.tasksUiInitialized = true;

    // 当页面DOM完全加载后执行
    $(document).ready(function() {
        // 加载导航栏
        $("#navbar-container").load("_navbar.html", function() {
            const currentUsername = localStorage.getItem('username');
            if (currentUsername) {
                $('#loggedInUser').text(currentUsername);
            }
            $('#logout-link').on('click', function(e) {
                e.preventDefault();
                logout();
            });
        });
        
        // 确保模态窗口可以正确滚动
        $('#taskFormModal').on('shown.bs.modal', function() {
            // 重置模态窗口滚动条位置到顶部
            $(this).find('.modal-body').scrollTop(0);
            
            // 确保高级设置部分可以通过滚动到达
            setTimeout(function() {
                // 延迟处理，确保模态窗口已完全渲染
                const modalBody = $('.modal-body');
                if (modalBody.prop('scrollHeight') > modalBody.height()) {
                    // 如果内容高度大于容器，确保滚动样式已正确应用
                    modalBody.css('overflow-y', 'auto');
                }
                
                // 确保表单字段和标签完全显示，不被截断
                $('.form-group label').css({
                    'display': 'block',
                    'white-space': 'normal',
                    'overflow': 'visible'
                });
                
                // 修复任务表单中可能的显示问题
                adjustFormElementsVisibility();
            }, 100);
        });
        
        // 确保工作流节点编辑模态窗口也能正确滚动
        $('#workflowNodeEditModal').on('shown.bs.modal', function() {
            $(this).find('.modal-body').scrollTop(0);
            setTimeout(function() {
                const nodeModalBody = $('#workflowNodeEditModal .modal-body');
                if (nodeModalBody.prop('scrollHeight') > nodeModalBody.height()) {
                    nodeModalBody.css('overflow-y', 'auto');
                }
                
                // 确保表单元素完全显示
                adjustFormElementsVisibility();
            }, 100);
        });
        
        // 确保边缘属性模态窗口也能正确滚动
        $('#edgePropertyModal').on('shown.bs.modal', function() {
            $(this).find('.modal-body').scrollTop(0);
            setTimeout(function() {
                const edgeModalBody = $('#edgePropertyModal .modal-body');
                if (edgeModalBody.prop('scrollHeight') > edgeModalBody.height()) {
                    edgeModalBody.css('overflow-y', 'auto');
                }
                
                // 确保表单元素完全显示
                adjustFormElementsVisibility();
            }, 100);
        });
        
        // 帮助函数：调整表单元素可见性和样式
        function adjustFormElementsVisibility() {
            // 确保标签和输入字段完全可见
            $('.form-row .form-group').each(function() {
                $(this).css({
                    'min-width': '200px',
                    'width': '100%',
                    'padding': '0 10px'
                });
                
                // 确保输入字段完全显示
                $(this).find('input, select, textarea').css({
                    'width': '100%',
                    'display': 'block',
                    'box-sizing': 'border-box'
                });
            });
            
            // 特别处理Bean任务字段和方法名称字段，确保它们完全可见
            $('#beanName, #methodName').css({
                'width': '100%'
            });
            
            // 重新调整form-row的布局以确保良好的响应式行为
            $('.form-row').css({
                'display': 'flex',
                'flex-wrap': 'wrap',
                'margin-right': '-10px',
                'margin-left': '-10px'
            });
        }
        
        // 使用全局初始化函数
        if (typeof window.initializeI18n === 'function') {
            window.initializeI18n().then(() => {
                console.log("i18n initialized for tasks.html");
                populateTaskTypeFilter();

                // 初始化完成后加载页面数据
                loadTasks();
                loadUsersForSelects();
                loadCalendarsForSelect();
            });
        } else {
            console.error("全局i18n初始化函数未定义，尝试使用传统方式初始化");
            // 回退到直接调用i18n.init
            if (typeof i18n !== 'undefined') {
                i18n.init().then(() => {
                    populateTaskTypeFilter();
                    loadTasks();
                    loadUsersForSelects();
                    loadCalendarsForSelect();
                });
            } else {
                console.error("i18n对象未定义，无法初始化国际化")
            }
            
            $("#navbar-container").load("_navbar.html", function () {
                const currentUsername = localStorage.getItem('username');
                if (currentUsername) {
                    $('#loggedInUser').text(currentUsername);
                }
                $('#logout-link').on('click', function (e) {
                    e.preventDefault();
                    logout();
                });
                if (typeof i18n !== 'undefined') {
                    i18n.applyTranslations(); // For the navbar
                }
            });
        }

        // 添加任务表单提交处理
        $('#task-form').submit(function (event) {
            event.preventDefault();
            const taskId = $('#taskId').val();
            const taskData = {
                taskName: $('#taskName').val(),
                taskGroup: $('#taskGroup').val(),
                cronExpression: $('#cronExpression').val(),
                description: $('#description').val(),
                executionMode: $('#executionMode').val(),
                taskType: parseInt($('#taskType').val()),
                isActive: $('#isActive').is(':checked'),
                beanName: $('#beanName').val(),
                methodName: $('#methodName').val(),
                startDate: $('#startDate').val() ? ($('#startDate').val() + ":00") : null, // Append seconds for backend parsing
                endDate: $('#endDate').val() ? ($('#endDate').val() + ":00") : null,         // Append seconds for backend parsing
                taskCalendarGroup: $('#taskCalendarGroup').val() || null,
                taskExcludeTimes: $('#taskExcludeTimes').val() || null,
                executeTimeoutSeconds: parseInt($('#executeTimeoutSeconds').val()) || 0,
                notifySuccessUserIds: $('#notifySuccessUserIds').val() ? $('#notifySuccessUserIds').val().join(',') : null,
                notifyFailedUserIds: $('#notifyFailedUserIds').val() ? $('#notifyFailedUserIds').val().join(',') : null,
                workflowNodes: null,
                workflowEdges: null,
                globalParameters: null,
                // 添加重试设置字段
                maxRetryAttempts: parseInt($('#maxRetries').val()) || 0,
                retryIntervalSeconds: parseInt($('#retryInterval').val()) || 30,
                retryIntervalMultiplier: parseFloat($('#retryIntervalMultiplier').val()) || 1.0
            };

            if (taskData.taskType === 0) {
                taskData.beanParameters = $('#beanParameters').val();
            } else if (taskData.taskType === 4) {
                const shellParams = {
                    script: $('#shellScriptContent').val(),
                    isInlineScript: $('#shellIsInlineScript').is(':checked'),
                    arguments: ($('#shellArguments').val() || '').split('\n').map(arg => arg.trim()).filter(arg => arg.length > 0),
                    workingDirectory: $('#shellWorkingDirectory').val() || null
                };
                taskData.beanParameters = JSON.stringify(shellParams);
                taskData.beanName = null;
                taskData.methodName = null;
            } else if (taskData.taskType === 2) {
                const httpParams = {
                    url: $('#httpUrl').val(),
                    method: $('#httpMethod').val(),
                    headers: parseJsonOrNull($('#httpHeaders').val()) || {},
                    body: $('#httpBody').val() || null,
                    connectTimeout: parseInt($('#httpConnectTimeout').val()) || 5000,
                    readTimeout: parseInt($('#httpReadTimeout').val()) || 30000,
                    retryCount: parseInt($('#httpRetryCount').val()) || 0,
                    successCode: $('#httpSuccessCode').val() || '200'
                };
                taskData.beanParameters = JSON.stringify(httpParams);
                taskData.beanName = null;
                taskData.methodName = null;
            } else if (taskData.taskType === 10) {
                taskData.workflowNodes = parseJsonOrNull($('#workflowNodesJson').val());
                taskData.workflowEdges = parseJsonOrNull($('#workflowEdgesJson').val());
                taskData.globalParameters = parseJsonOrNull($('#globalParametersJson').val());
                taskData.beanName = null;
                taskData.methodName = null;
                taskData.beanParameters = null;
            }

            const method = taskId ? 'PUT' : 'POST';
            const endpoint = taskId ? `/tasks/${taskId}` : '/tasks';

            makeApiCall(method, endpoint, taskData,
                function (response) {
                    $('#taskFormModal').modal('hide');
                    showFeedback(i18n.translate('tasksPage.feedback.taskSaved', 'Task saved successfully!'), false);
                    loadTasks();
                },
                function (jqXHR) {
                    showFeedback(i18n.translate('tasksPage.feedback.errorSaving', "Error saving task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                }
            );
        });

        // 任务类型变更处理
        $('#taskType').change(function () {
            const type = $(this).val();
            $('.type-specific-fields').hide();
            $('#beanName, #methodName, #beanParameters').closest('.form-group').show();

            const initialDagMsg = i18n.translate('tasksPage.modal.workflowTaskFields.dagContainerPlaceholderInitial', "Define nodes and edges in the JSON textareas and click \"Refresh View\".");
            $('#dagContainer').empty().html(`<p class="text-muted small">${initialDagMsg}</p>`);

            if (type === '0') {
                $('#beanTaskFields').show();
            } else if (type === '4') {
                $('#shellTaskFields').show();
                $('#beanName, #methodName, #beanParameters').closest('.form-group').hide();
            } else if (type === '2') {
                $('#httpTaskFields').show();
                $('#beanName, #methodName, #beanParameters').closest('.form-group').hide();
            } else if (type === '10') {
                $('#workflowTaskFields').show();
                $('#beanName, #methodName, #beanParameters').closest('.form-group').hide();
                loadAvailableTasksForNodes();
                redrawDAG();
            }
            
            // 确保国际化应用到新显示的字段
            setTimeout(() => {
                i18n.applyTranslations();
                console.log("已在任务类型切换后重新应用国际化");
            }, 100);
        }).trigger('change');

        // 刷新DAG视图
        $('#refreshDagViewBtn').on('click', function () {
            redrawDAG();
        });

        // 编辑按钮点击事件
        $('#tasks-table-body').on('click', '.edit-btn', function () {
            const taskId = $(this).data('id');
            
            // 清除表单
            $('#task-form')[0].reset();
            $('#taskId').val(taskId);
            
            // 获取任务详情并填充表单
            makeApiCall('GET', `/tasks/${taskId}`, null,
                function (task) {
                    $('#taskName').val(task.taskName);
                    $('#taskGroup').val(task.taskGroup);
                    $('#taskType').val(task.taskType.toString()).trigger('change');
                    $('#cronExpression').val(task.cronExpression);
                    $('#description').val(task.description);
                    $('#executionMode').val(task.executionMode || 'BROADCAST');
                    $('#isActive').prop('checked', task.isActive);

                    // 处理特定类型任务的额外字段
                    if (task.taskType === 0) { // Bean Task
                        $('#beanName').val(task.beanName);
                        $('#methodName').val(task.methodName);
                        if (task.beanParameters) {
                            try {
                                const params = typeof task.beanParameters === 'string' ? 
                                    JSON.parse(task.beanParameters) : task.beanParameters;
                                $('#beanParameters').val(JSON.stringify(params, null, 2));
                            } catch (e) {
                                $('#beanParameters').val(task.beanParameters);
                                console.warn('Failed to parse bean parameters JSON:', e);
                            }
                        }
                    } else if (task.taskType === 2) { // HTTP Task
                        if (task.beanParameters) {
                            try {
                                const httpParams = typeof task.beanParameters === 'string' ? 
                                    JSON.parse(task.beanParameters) : task.beanParameters;
                                $('#httpUrl').val(httpParams.url || '');
                                $('#httpMethod').val(httpParams.method || 'GET');
                                $('#httpHeaders').val(httpParams.headers ? JSON.stringify(httpParams.headers, null, 2) : '{}');
                                $('#httpBody').val(httpParams.body || '');
                                $('#httpConnectTimeout').val(httpParams.connectTimeout || 5000);
                                $('#httpReadTimeout').val(httpParams.readTimeout || 30000);
                                $('#httpRetryCount').val(httpParams.retryCount || 0);
                                $('#httpSuccessCode').val(httpParams.successCode || '200');
                            } catch (e) {
                                console.warn('Failed to parse HTTP task parameters JSON:', e);
                            }
                        }
                    } else if (task.taskType === 4) { // Shell Task
                        if (task.beanParameters) {
                            try {
                                const shellParams = typeof task.beanParameters === 'string' ? 
                                    JSON.parse(task.beanParameters) : task.beanParameters;
                                $('#shellScriptContent').val(shellParams.script || '');
                                $('#shellIsInlineScript').prop('checked', !!shellParams.isInlineScript);
                                $('#shellWorkingDirectory').val(shellParams.workingDirectory || '');
                                if (shellParams.arguments && Array.isArray(shellParams.arguments)) {
                                    $('#shellArguments').val(shellParams.arguments.join('\n'));
                                }
                            } catch (e) {
                                console.warn('Failed to parse Shell task parameters JSON:', e);
                            }
                        }
                    } else if (task.taskType === 10) { // Workflow Task
                        if (task.beanParameters) {
                            try {
                                const workflowParams = typeof task.beanParameters === 'string' ? 
                                    JSON.parse(task.beanParameters) : task.beanParameters;
                                $('#workflowNodesJson').val(JSON.stringify(workflowParams.nodes || [], null, 2));
                                $('#workflowEdgesJson').val(JSON.stringify(workflowParams.edges || [], null, 2));
                                redrawDAG();
                            } catch (e) {
                                console.warn('Failed to parse Workflow task parameters JSON:', e);
                            }
                        }
                    }

                    // 日期时间处理
                    $('#taskExcludeTimes').val(task.taskExcludeTimes);
                    $('#executeTimeoutSeconds').val(task.executeTimeoutSeconds != null ? task.executeTimeoutSeconds : 0);

                    // 加载重试配置
                    $('#maxRetries').val(task.maxRetryAttempts != null ? task.maxRetryAttempts : 0);
                    $('#retryInterval').val(task.retryIntervalSeconds != null ? task.retryIntervalSeconds : 30);
                    $('#retryIntervalMultiplier').val(task.retryIntervalMultiplier != null ? task.retryIntervalMultiplier : 1.0);

                    $('#taskCalendarGroup').val(task.taskCalendarGroup || '');
                    const successUserIds = task.notifySuccessUserIds ? task.notifySuccessUserIds.split(',') : [];
                    $('#notifySuccessUserIds').val(successUserIds);
                    const failedUserIds = task.notifyFailedUserIds ? task.notifyFailedUserIds.split(',') : [];
                    $('#notifyFailedUserIds').val(failedUserIds);

                    $('#taskFormModal').modal('show');
                },
                function (jqXHR) {
                    showFeedback(i18n.translate('tasksPage.feedback.errorLoadingTaskDetails', "Error fetching task details: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                }
            );
        });

        // 删除按钮点击事件
        $('#tasks-table-body').on('click', '.delete-btn', function () {
            const taskId = $(this).data('id');
            if (confirm(i18n.translate('tasksPage.feedback.confirmDelete', "Are you sure you want to delete task {{taskId}}?").replace('{{taskId}}', taskId))) {
                makeApiCall('DELETE', `/tasks/${taskId}`, null,
                    function () {
                        showFeedback(i18n.translate('tasksPage.feedback.taskDeleted'), false);
                        loadTasks();
                    },
                    function (jqXHR) {
                        showFeedback(i18n.translate('tasksPage.feedback.errorDeleting', "Error deleting task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                    }
                );
            }
        });

        // 触发按钮点击事件
        $('#tasks-table-body').on('click', '.trigger-btn', function () {
            const taskId = $(this).data('id');
            makeApiCall('POST', `/tasks/${taskId}/trigger`, null,
                function () {
                    showFeedback(i18n.translate('tasksPage.feedback.taskTriggered', "Task {{taskId}} triggered successfully!").replace('{{taskId}}', taskId), false);
                },
                function (jqXHR) {
                    showFeedback(i18n.translate('tasksPage.feedback.errorTriggering', "Error triggering task: {{error}}").replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                }
            );
        });

        // 启用/禁用按钮点击事件
        $('#tasks-table-body').on('click', '.enable-btn, .disable-btn', function () {
            const taskId = $(this).data('id');
            const isEnable = $(this).hasClass('enable-btn');
            const endpoint = `/api/tasks/${taskId}/${isEnable ? 'enable' : 'disable'}`;
            
            makeApiCall('POST', endpoint, null,
                function () {
                    const messageKey = isEnable ? 'tasksPage.feedback.taskEnabled' : 'tasksPage.feedback.taskDisabled';
                    const defaultMessage = isEnable ? "Task {{taskId}} enabled successfully!" : "Task {{taskId}} disabled successfully!";
                    showFeedback(i18n.translate(messageKey, defaultMessage).replace('{{taskId}}', taskId), false);
                    loadTasks();
                },
                function (jqXHR) {
                    const messageKey = isEnable ? 'tasksPage.feedback.errorEnabling' : 'tasksPage.feedback.errorDisabling';
                    const defaultMessage = isEnable ? "Error enabling task: {{error}}" : "Error disabling task: {{error}}";
                    showFeedback(i18n.translate(messageKey, defaultMessage).replace("{{error}}", (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
                }
            );
        });

        // 保存边缘按钮点击事件
        $('#saveEdgeBtn').on('click', function () {
            const fromNode = $('#edgeModalFromNode').val();
            const toNode = $('#edgeModalToNode').val();
            const expression = $('#edgeExpression').val();
            const priority = parseInt($('#edgePriority').val()) || 0;
            const isEditing = $('#edgeModalIsEditing').val() === 'true';
            const arrayIndex = parseInt($('#edgeModalArrayIndex').val());

            let currentNodesJson = $('#workflowNodesJson').val();
            let nodesArray = [];
            try {
                if (currentNodesJson && currentNodesJson.trim() !== '') {
                    nodesArray = JSON.parse(currentNodesJson);
                    if (!Array.isArray(nodesArray)) {
                        // 处理非数组情况
                    }
                }
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeHelperErrorParseNodes', 'Error parsing Workflow Nodes JSON.') + ': ' + e.message, true);
                return;
            }

            let currentEdgesJson = $('#workflowEdgesJson').val();
            let potentialEdgesArray = [];
            try {
                if (currentEdgesJson && currentEdgesJson.trim() !== '') {
                    potentialEdgesArray = JSON.parse(currentEdgesJson);
                    if (!Array.isArray(potentialEdgesArray)) {
                        // 处理非数组情况
                    }
                }
            } catch (e) {
                potentialEdgesArray = [];
            }
            potentialEdgesArray = JSON.parse(JSON.stringify(potentialEdgesArray)); // 深拷贝

            const newEdge = {
                fromNodeId: fromNode,
                toNodeId: toNode,
                expression: expression || null,
                priority: priority
            };

            if (isEditing && arrayIndex > -1 && arrayIndex < potentialEdgesArray.length) {
                potentialEdgesArray[arrayIndex] = newEdge;
            } else {
                potentialEdgesArray.push(newEdge);
            }

            // 环检测
            if (nodesArray.length > 0 && potentialEdgesArray.length > 0) {
                if (detectCycleInWorkflow(nodesArray, potentialEdgesArray)) {
                    // 检测到环，显示警告信息，但仍然允许用户保存
                    const cycleWarning = i18n.translate('tasksPage.feedback.cycleDetected', 
                        'Warning: A cycle was detected in the workflow graph. This may cause infinite loops during execution. Proceed with caution.');
                    showFeedback(cycleWarning, true);
                }
            }

            $('#workflowEdgesJson').val(JSON.stringify(potentialEdgesArray, null, 2));
            $('#edgePropertyModal').modal('hide');
            redrawDAG();
        });

        // 删除边缘按钮点击事件
        $('#deleteEdgeBtn').on('click', function () {
            const arrayIndex = parseInt($('#edgeModalArrayIndex').val());
            let edgesJson = $('#workflowEdgesJson').val();
            let edgesArray = [];

            try {
                edgesArray = JSON.parse(edgesJson || '[]');
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.edgeErrorParse', "Error parsing existing Workflow Edges JSON: {{message}}. Starting with new array.").replace("{{message}}", e.message), true);
                return;
            }

            if (arrayIndex > -1 && arrayIndex < edgesArray.length) {
                edgesArray.splice(arrayIndex, 1);
                $('#workflowEdgesJson').val(JSON.stringify(edgesArray, null, 2));
            } else {
                showFeedback(i18n.translate('tasksPage.feedback.edgeDeleteError'), true);
            }
            $('#edgePropertyModal').modal('hide');
            redrawDAG();
        });

        // 边属性模态框显示事件
        $('#edgePropertyModal').on('show.bs.modal', function () {
            // 检查是新建还是编辑模式
            const isEditing = $('#edgeModalIsEditing').val() === 'true';
            
            // 设置帮助文本
            const expressionHelpText = i18n.translate('tasksPage.edgeModal.expressionHelp', 
                '可以使用表达式决定边缘是否激活。例如：<br>' +
                '- <code>${nodeA_status} == \'SUCCESS\'</code> 当节点A成功时<br>' + 
                '- <code>${nodeB_result} > 100</code> 当节点B结果大于100时<br>' +
                '留空则表示无条件激活。');
            
            const priorityHelpText = i18n.translate('tasksPage.edgeModal.priorityHelp',
                '当多条边缘激活时，优先级越高（数值越大）的边缘越先被执行。<br>' +
                '默认值为0，相同优先级则按添加顺序执行。');
            
            // 添加或更新帮助文本，添加Bootstrap提示框样式
            $('#expressionHelpText').remove();
            $('<small id="expressionHelpText" class="form-text text-muted"></small>')
                .html(expressionHelpText)
                .insertAfter('#edgeExpression');
                
            $('#priorityHelpText').remove();
            $('<small id="priorityHelpText" class="form-text text-muted"></small>')
                .html(priorityHelpText)
                .insertAfter('#edgePriority');
                
            // 为表达式字段添加自动完成功能
            const fromNodeId = $('#edgeModalFromNode').val();
            
            // 常见变量的自动完成提示
            const autoCompleteTerms = [
                `\${${fromNodeId}_status}`,
                `\${${fromNodeId}_result}`,
                `\${${fromNodeId}_execution_time}`,
                '\'SUCCESS\'', 
                '\'FAILED\'',
                '\'COMPLETED\'',
                '==', 
                '!=', 
                '>', 
                '<', 
                '>=', 
                '<='
            ];
            
            // 假设我们这里使用简单的自动填充功能，后续可以考虑集成更复杂的库
            $('#edgeExpression').attr('list', 'expressionSuggestions');
            
            // 移除之前的数据列表，避免重复添加
            $('#expressionSuggestions').remove();
            
            // 添加数据列表
            const datalist = $('<datalist id="expressionSuggestions"></datalist>');
            autoCompleteTerms.forEach(term => {
                datalist.append($('<option></option>').val(term));
            });
            
            $('#edgeExpression').after(datalist);
        });
        
        // 边属性模态框隐藏事件
        $('#edgePropertyModal').on('hidden.bs.modal', function () {
            if (window.selectedSourceElement) {
                window.selectedSourceElement.find('rect').attr('fill', '#fff');
            }
            window.selectedSourceNodeId = null;
            window.selectedSourceElement = null;
            $('#edgeExpression').val('');
            $('#edgePriority').val('0');
            $('#edgeModalIsEditing').val('false');
            $('#edgeModalArrayIndex').val('-1');
            $('#deleteEdgeBtn').hide();
        });

        // DAG容器点击事件
        $('#dagContainer').on('click', function (event) {
            const target = $(event.target);
            if (window.selectedSourceElement && (
                target.is('#dagContainer') || target.is('svg') || 
                target.is('svg > defs') || target.is('svg > marker') || 
                target.is('svg > path') || target.is('svg > rect') || 
                target.is('svg > text')
            )) {
                // 处理点击事件
            }
        });

        // 保存节点更改按钮点击事件
        $('#saveNodeChangesBtn').on('click', function () {
            const updatedNodeName = $('#editNodeName').val().trim();
            const updatedNodeParamsString = $('#editNodeParams').val();
            const nodeIndex = parseInt($('#editingNodeArrayIndex').val());

            let updatedParams;
            try {
                updatedParams = updatedNodeParamsString.trim() === '' ? {} : JSON.parse(updatedNodeParamsString);
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeParamsInvalid', 'JSON参数格式无效:') + ' ' + e.message, true);
                return;
            }

            // 处理新节点创建或现有节点编辑
            if (nodeIndex === -1) {
                // 创建新节点
                const nodeId = $('#editNodeId').val().trim();
                const taskConfigId = $('#editTaskConfigId').val();
                
                if (!nodeId) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeIdRequired', '节点ID是必需的'), true);
                    return;
                }
                
                let currentNodesJson = $('#workflowNodesJson').val();
                let nodesArray = [];
                try {
                    nodesArray = JSON.parse(currentNodesJson || '[]');
                } catch (e) {
                    nodesArray = [];
                }
                
                // 检查节点ID是否重复
                if (nodesArray.some(n => n.nodeId === nodeId)) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeIdDuplicate', '节点ID已存在: ') + nodeId, true);
                    return;
                }
                
                // 创建新节点对象
                const newNode = {
                    nodeId: nodeId,
                    nodeName: updatedNodeName || nodeId,
                    parameters: updatedParams
                };
                
                if (taskConfigId && taskConfigId !== '') {
                    newNode.taskConfigId = parseInt(taskConfigId);
                }
                
                // 如果处于图形模式，添加位置信息 - 默认放在中心位置
                if (window.graphModeEnabled) {
                    const container = $('#dagContainer');
                    const containerWidth = container.width();
                    const containerHeight = container.height();
                    
                    newNode.position = {
                        x: (containerWidth / 2) - 75, // 节点宽度的一半
                        y: (containerHeight / 2) - 30  // 节点高度的一半
                    };
                }
                
                // 添加到节点数组
                nodesArray.push(newNode);
                $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
                
                showFeedback(i18n.translate('tasksPage.feedback.nodeAdded', '节点已添加: ') + nodeId, false);
            } else {
                // 编辑现有节点
                let currentNodesJson = $('#workflowNodesJson').val();
                let nodesArray = [];
                try {
                    nodesArray = JSON.parse(currentNodesJson || '[]');
                } catch (e) {
                    showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析工作流节点时出错: ') + e.message, true);
                    return;
                }
                
                if (nodeIndex >= 0 && nodeIndex < nodesArray.length) {
                    // 获取选择的任务配置ID
                    const taskConfigId = $('#editTaskConfigId').val();
                    
                    // 更新节点信息
                    nodesArray[nodeIndex].nodeName = updatedNodeName;
                    nodesArray[nodeIndex].parameters = updatedParams;
                    
                    // 更新任务配置ID（如果选择了）
                    if (taskConfigId) {
                        nodesArray[nodeIndex].taskConfigId = parseInt(taskConfigId);
                    }
                    
                    $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
                    showFeedback(i18n.translate('tasksPage.feedback.nodeUpdated', '节点已更新: ') + nodesArray[nodeIndex].nodeId, false);
                } else {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeNotFound', '找不到要编辑的节点'), true);
                    return;
                }
            }
            
            $('#workflowNodeEditModal').modal('hide');
            redrawDAG();
        });

        // 工作流节点编辑模态框隐藏事件
        $('#workflowNodeEditModal').on('hidden.bs.modal', function () {
            $('#editNodeName').val('');
            $('#editNodeParams').val('{}');
            $('#displayNodeId').text('');
            $('#editTaskConfigId').empty(); // 清空任务选择下拉框
            $('#editingNodeArrayIndex').val('');
        });

        // 添加工作流DAG上下文菜单功能
        $('#dagContainer').on('contextmenu', function(e) {
            // 移除之前的任何上下文菜单
            $('.dag-context-menu').remove();
            
            // 防止默认的右键菜单
            e.preventDefault();
            
            // 获取鼠标位置
            const mouseX = e.pageX;
            const mouseY = e.pageY;
            
            // 创建上下文菜单
            const contextMenu = $('<div>')
                .addClass('dag-context-menu')
                .css({
                    position: 'absolute',
                    top: mouseY + 'px',
                    left: mouseX + 'px',
                    background: 'white',
                    border: '1px solid #ccc',
                    borderRadius: '3px',
                    boxShadow: '0 2px 5px rgba(0,0,0,0.2)',
                    padding: '5px 0',
                    zIndex: 1000
                });
            
            // 添加菜单项
            const menuItems = [
                {
                    text: i18n.translate('tasksPage.dagContextMenu.refreshView', '刷新视图'),
                    icon: 'bi-arrow-repeat',
                    action: function() {
                        redrawDAG();
                    }
                },
                {
                    text: i18n.translate('tasksPage.dagContextMenu.toggleGuide', '显示/隐藏操作指南'),
                    icon: 'bi-info-circle',
                    action: function() {
                        const guide = $('#dagContainer').find('.dag-operation-guide');
                        if (guide.length) {
                            guide.toggle();
                        } else {
                            $('#dagContainer').append(window.showDagOperationGuide());
                        }
                    }
                },
                {
                    text: i18n.translate('tasksPage.dagContextMenu.formatJson', '格式化JSON数据'),
                    icon: 'bi-braces',
                    action: function() {
                        try {
                            // 美化节点JSON
                            const nodesJson = $('#workflowNodesJson').val();
                            if (nodesJson.trim() !== '') {
                                const parsedNodes = JSON.parse(nodesJson);
                                $('#workflowNodesJson').val(JSON.stringify(parsedNodes, null, 2));
                            }
                            
                            // 美化边缘JSON
                            const edgesJson = $('#workflowEdgesJson').val();
                            if (edgesJson.trim() !== '') {
                                const parsedEdges = JSON.parse(edgesJson);
                                $('#workflowEdgesJson').val(JSON.stringify(parsedEdges, null, 2));
                            }
                            
                            showFeedback(i18n.translate('tasksPage.feedback.jsonFormatted', 'JSON已成功格式化'), false);
                        } catch (e) {
                            showFeedback(i18n.translate('tasksPage.feedback.jsonFormatError', 'JSON格式化失败: ') + e.message, true);
                        }
                    }
                }
            ];
            
            // 为每个菜单项创建DOM元素
            menuItems.forEach(function(item) {
                const menuItem = $('<div>')
                    .addClass('dag-context-menu-item')
                    .css({
                        padding: '6px 15px',
                        cursor: 'pointer',
                        whiteSpace: 'nowrap'
                    })
                    .hover(
                        function() { $(this).css('background-color', '#f0f0f0'); },
                        function() { $(this).css('background-color', ''); }
                    )
                    .on('click', function() {
                        $('.dag-context-menu').remove();
                        item.action();
                    });
                
                // 添加图标和文本
                if (item.icon) {
                    menuItem.append($('<i>').addClass(item.icon).css('margin-right', '8px'));
                }
                menuItem.append(document.createTextNode(item.text));
                
                contextMenu.append(menuItem);
            });
            
            // 将菜单添加到DOM
            $('body').append(contextMenu);
            
            // 点击其他地方关闭菜单
            $(document).on('click', function closeMenu(e) {
                if (!$(e.target).closest('.dag-context-menu').length) {
                    $('.dag-context-menu').remove();
                    $(document).off('click', closeMenu);
                }
            });
        });

        // 添加任务按钮点击事件
        $('#addTaskBtn').click(function () {
            $('#taskType').trigger('change');
        });

        // 验证CRON表达式按钮点击事件
        $('#validateCronBtn').on('click', function () {
            const cronExpression = $('#cronExpression').val();
            if (!cronExpression || cronExpression.trim() === '') {
                const errorMsg = i18n.translate('tasksPage.modal.missingCronError', 'Please provide a valid CRON expression');
                $('#cronValidationResult').html(`<div class="alert alert-danger">${errorMsg}</div>`).show();
                return;
            }
            
            // 显示加载指示
            $('#cronValidationResult').html(`<div class="text-center"><div class="spinner-border spinner-border-sm text-primary" role="status"></div> ${i18n.translate('common.loading', 'Loading...')}</div>`).show();
            
            // 调用API验证CRON表达式
            makeApiCall('POST', '/tasks/validate-cron', { cronExpression: cronExpression },
                function (response) {
                    if (response && response.isValid) {
                        let nextRunsHtml = '';
                        if (response.nextRuntimes && response.nextRuntimes.length > 0) {
                            const nextRunsLabel = i18n.translate('tasksPage.modal.nextRuns', 'Next runs:');
                            nextRunsHtml = `<p class="mt-2"><strong>${nextRunsLabel}</strong><br>`;
                            // 显示最多5个未来的运行时间
                            for (let i = 0; i < Math.min(response.nextRuntimes.length, 5); i++) {
                                nextRunsHtml += `${new Date(response.nextRuntimes[i]).toLocaleString()}<br>`;
                            }
                            nextRunsHtml += '</p>';
                        }
                        $('#cronValidationResult').html(`<div class="alert alert-success">${response.message}</div>${nextRunsHtml}`).show();
                    } else {
                        const invalidCronMsg = i18n.translate('tasksPage.modal.invalidCronExpression', 'Invalid CRON expression');
                        $('#cronValidationResult').html(`<div class="alert alert-danger">${invalidCronMsg}: ${response ? response.message : ''}</div>`).show();
                    }
                },
                function (jqXHR) {
                    const errorMsg = jqXHR.responseJSON && jqXHR.responseJSON.message 
                        ? jqXHR.responseJSON.message 
                        : i18n.translate('tasksPage.modal.invalidCronExpression', 'Invalid CRON expression');
                    $('#cronValidationResult').html(`<div class="alert alert-danger">${errorMsg}</div>`).show();
                }
            );
        });

        // 应用过滤器按钮点击事件
        $('#applyTaskFiltersBtn').click(function() {
            loadTasks();
        });

        // 重置过滤器按钮点击事件
        $('#resetTaskFiltersBtn').click(function() {
            $('#filterTaskName').val('');
            $('#filterTaskGroup').val('');
            $('#filterTaskType').val('');
            $('#filterIsActive').val('');
            loadTasks();
        });

        // 工作流添加/更新节点按钮实现
        $('#addNodeToJsonBtn').on('click', function() {
            const taskId = $('#availableTasksForNodes').val();
            const nodeId = $('#wfNodeId').val().trim();
            const nodeName = $('#wfNodeName').val().trim();
            const nodeParams = $('#wfNodeParams').val().trim();

            if (!nodeId) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeIdRequired', 'Node ID is required.'), true);
                return;
            }

            // 尝试解析节点参数
            let parsedParams = {};
            if (nodeParams) {
                try {
                    parsedParams = JSON.parse(nodeParams);
                } catch (e) {
                    showFeedback(i18n.translate('tasksPage.feedback.nodeParamsInvalid', 'Invalid node parameters JSON.'), true);
                    return;
                }
            }

            // 准备新节点
            const newNode = {
                nodeId: nodeId,
                nodeName: nodeName || nodeId,
                parameters: parsedParams
            };

            if (taskId) {
                newNode.taskConfigId = parseInt(taskId);
            }

            // 读取现有节点
            let existingNodes = [];
            try {
                const nodesJson = $('#workflowNodesJson').val();
                existingNodes = nodesJson && nodesJson.trim() !== '' ? JSON.parse(nodesJson) : [];
            } catch (e) {
                showFeedback(i18n.translate('tasksPage.feedback.nodeHelperErrorParseNodes', 'Error parsing workflow nodes JSON.'), true);
                existingNodes = [];
            }

            // 检查是否有同ID节点
            const existingNodeIndex = existingNodes.findIndex(node => node.nodeId === nodeId);
            if (existingNodeIndex >= 0) {
                // 更新现有节点
                existingNodes[existingNodeIndex] = { ...existingNodes[existingNodeIndex], ...newNode };
                showFeedback(i18n.translate('tasksPage.feedback.nodeUpdated', 'Node {{nodeId}} updated.').replace('{{nodeId}}', nodeId), false);
            } else {
                // 添加新节点
                existingNodes.push(newNode);
                showFeedback(i18n.translate('tasksPage.feedback.nodeAdded', 'Node {{nodeId}} added.').replace('{{nodeId}}', nodeId), false);
            }

            // 更新节点JSON
            $('#workflowNodesJson').val(JSON.stringify(existingNodes, null, 2));

            // 清除表单
            $('#wfNodeId').val('');
            $('#wfNodeName').val('');
            $('#wfNodeParams').val('{}');
            $('#availableTasksForNodes').val('');
            
            // 刷新DAG视图
            redrawDAG();
        });

        // 快速添加节点
        $('.quick-add-node').on('click', function(e) {
            e.preventDefault();
            const nodeType = $(this).data('node-type');
            
            // 生成唯一ID
            const timestamp = new Date().getTime();
            const randomPart = Math.floor(Math.random() * 1000);
            
            let nodeId, nodeName, parameters = {};
            
            switch(nodeType) {
                case 'start':
                    nodeId = 'start_' + timestamp % 10000;
                    nodeName = '开始节点';
                    parameters = { 
                        isStartNode: true,
                        description: '工作流起始点'
                    };
                    break;
                case 'end':
                    nodeId = 'end_' + timestamp % 10000;
                    nodeName = '结束节点';
                    parameters = { 
                        isEndNode: true,
                        description: '工作流终止点'
                    };
                    break;
                case 'process':
                    nodeId = 'process_' + timestamp % 10000;
                    nodeName = '处理节点';
                    parameters = { 
                        processType: 'standard',
                        description: '执行标准处理'
                    };
                    break;
                case 'decision':
                    nodeId = 'decision_' + timestamp % 10000;
                    nodeName = '决策节点';
                    parameters = { 
                        decisionType: 'condition',
                        description: '基于条件分支执行流'
                    };
                    break;
                case 'parallel':
                    nodeId = 'parallel_' + timestamp % 10000;
                    nodeName = '并行节点';
                    parameters = { 
                        parallelType: 'fork',
                        description: '创建并行执行分支'
                    };
                    break;
                default:
                    nodeId = 'node_' + timestamp % 10000;
                    nodeName = '普通节点';
                    parameters = {};
            }
            
            // 读取现有节点
            let nodesArray = [];
            try {
                const nodesJson = $('#workflowNodesJson').val();
                nodesArray = nodesJson && nodesJson.trim() !== '' ? JSON.parse(nodesJson) : [];
            } catch(e) {
                showFeedback(i18n.translate('tasksPage.feedback.errorParsingNodes', '解析节点JSON时出错'), true);
                return;
            }
            
            // 创建新节点对象
            const newNode = {
                nodeId: nodeId,
                nodeName: nodeName,
                parameters: parameters
            };
            
            // 如果处于图形模式，添加位置信息并优化节点放置
            if (window.graphModeEnabled) {
                const container = $('#dagContainer');
                const svg = container.find('svg');
                
                // 获取可见区域的中心点
                const containerWidth = container.width();
                const containerHeight = container.height();
                const scrollLeft = container.scrollLeft();
                const scrollTop = container.scrollTop();
                
                // 计算良好的节点布局位置
                const existingNodes = nodesArray.length;
                let posX, posY;
                
                if (existingNodes === 0) {
                    // 第一个节点放在中央
                    posX = (containerWidth / 2) - 75; // 节点宽度的一半
                    posY = 50; // 顶部留出一些空间
                } else if (nodeType === 'start') {
                    // 开始节点放在顶部中央
                    posX = (containerWidth / 2) - 75;
                    posY = 30;
                } else if (nodeType === 'end') {
                    // 结束节点放在现有节点下方
                    posX = (containerWidth / 2) - 75;
                    posY = Math.max(...nodesArray.map(n => (n.position?.y || 0) + 100));
                } else {
                    // 对于其他节点，根据类型添加到适当位置
                    const existingPositions = nodesArray.map(n => n.position || {x: 0, y: 0});
                    
                    // 找到右下角的空白区域
                    const maxY = Math.max(100, ...existingPositions.map(p => p.y || 0));
                    const maxX = Math.max(100, ...existingPositions.map(p => p.x || 0));
                    
                    // 添加一些随机偏移，避免节点完全重叠
                    const randomOffsetX = Math.floor(Math.random() * 50);
                    const randomOffsetY = Math.floor(Math.random() * 50);
                    
                    if (nodeType === 'decision' || nodeType === 'parallel') {
                        // 决策和并行节点倾向于放在中间
                        posX = (containerWidth / 2) - 75 + randomOffsetX;
                        posY = maxY + 40 + randomOffsetY;
                    } else {
                        // 其他节点放在右侧
                        posX = maxX + 50 + randomOffsetX;
                        posY = 100 + randomOffsetY;
                    }
                }
                
                // 保证节点在可见区域内
                posX = Math.max(10, Math.min(posX, containerWidth - 160));
                posY = Math.max(10, posY);
                
                // 设置节点位置
                newNode.position = {
                    x: posX,
                    y: posY
                };
            }
            
            // 添加到数组
            nodesArray.push(newNode);
            
            // 更新JSON
            $('#workflowNodesJson').val(JSON.stringify(nodesArray, null, 2));
            
            // 重绘DAG
            redrawDAG();
            
            showFeedback(i18n.translate('tasksPage.feedback.quickNodeAdded', '已添加 ') + nodeName, false);
        });

        // 添加节点模态框显示函数
        window.showAddNodeModal = function() {
            // 确保节点助手区域和内容都可见
            const nodeHelperCard = $('#nodeHelperCard');
            const nodeHelperContent = $('#nodeHelperContent');
            
            nodeHelperCard.show();
            nodeHelperContent.show();
            $('#toggleNodeHelper i').removeClass('bi-chevron-down').addClass('bi-chevron-up');
            localStorage.setItem('nodeHelperHidden', 'false');
            
            // 滚动到节点助手区域，使其可见
            if (nodeHelperCard.length) {
                $('html, body').animate({
                    scrollTop: nodeHelperCard.offset().top - 100
                }, 300);
                
                // 高亮节点助手区域，提示用户
                nodeHelperCard.addClass('border-primary').css('box-shadow', '0 0 10px rgba(0,123,255,0.5)');
                setTimeout(function() {
                    nodeHelperCard.removeClass('border-primary').css('box-shadow', '');
                }, 2000);
            }
            
            // 显示添加节点的表单
            $('#availableTasksForNodes').focus();
            
            // 确保任务列表已加载
            loadAvailableTasksForNodes();
        };

        // 通用处理程序，用于所有"Format JSON"按钮
        $('body').on('click', '.format-json-btn', function () {
            const textareaId = $(this).data('target-textarea');
            const $textarea = $('#' + textareaId);
            if ($textarea.length) {
                const currentJson = $textarea.val();
                if (currentJson.trim() === '') {
                    // 可选地，显示没有内容可格式化的反馈，或者什么都不做
                    return;
                }
                try {
                    const parsedJson = JSON.parse(currentJson);
                    const formattedJson = JSON.stringify(parsedJson, null, 2); // 美化打印，缩进2个空格
                    $textarea.val(formattedJson);
                    showFeedback(i18n.translate('tasksPage.feedback.jsonFormatted', 'JSON formatted successfully.'), false);
                } catch (e) {
                    const errorMsg = i18n.translate('tasksPage.feedback.invalidJsonFormat', 'Invalid JSON: Could not format. Please check syntax. {{error}}')
                        .replace('{{error}}', e.message);
                    showFeedback(errorMsg, true);
                }
            } else {
                console.warn('Format JSON button clicked, but target textarea not found:', textareaId);
            }
        });

        // 节点助手切换按钮点击事件
        $(document).on('click', '#toggleNodeHelper', function() {
            const nodeHelperContent = $('#nodeHelperContent');
            if (nodeHelperContent.is(':visible')) {
                nodeHelperContent.slideUp();
                $(this).find('i').removeClass('bi-chevron-up').addClass('bi-chevron-down');
                localStorage.setItem('nodeHelperHidden', 'true');
                console.log("Node helper hidden by user");
            } else {
                nodeHelperContent.slideDown();
                $(this).find('i').removeClass('bi-chevron-down').addClass('bi-chevron-up');
                localStorage.setItem('nodeHelperHidden', 'false');
                console.log("Node helper shown by user");
                
                // 当用户显示节点助手时，确保任务列表已加载
                if (window.graphModeEnabled) {
                    loadAvailableTasksForNodes();
                }
            }
        });
        
        // 工作流编辑模式切换处理
        $('#editorModeForm, #editorModeGraph').on('click', function() {
            const isFormMode = $(this).attr('id') === 'editorModeForm';
            
            // 更新按钮状态
            $('#editorModeForm, #editorModeGraph').removeClass('active');
            $(this).addClass('active');
            
            // 更新控件显示
            if (isFormMode) {
                $('#formModeControls').show();
                $('#graphModeControls').hide();
                $('#edgeDefinitionForm').show();
                // 表单模式下，显示节点和边缘的JSON编辑区和节点助手
                $('.workflow-json-editor').show();
                $('#nodeHelperCard').show(); // 显示节点助手卡片
                $('#workflowNodesJson, #workflowEdgesJson').closest('.form-group').show();
                $('#globalParametersJson').closest('.form-group').show();
                
                // 表单模式下，强制显示节点助手内容
                window.ensureNodeHelperVisible(true);
                
                // 刷新DAG视图
                redrawDAG(); 
                
                // 更改帮助文本
                $('#dagHelpText').text(i18n.translate('tasksPage.modal.workflowTaskFields.formModeHelp', 
                    '表单模式：通过上方节点助手和JSON编辑区管理工作流'));
            } else {
                $('#formModeControls').hide();
                $('#graphModeControls').show();
                $('#edgeDefinitionForm').hide();
                // 图形模式下，隐藏节点和边缘的JSON编辑区，根据用户设置显示/隐藏节点助手
                $('#workflowNodesJson, #workflowEdgesJson').closest('.form-group').hide();
                
                // 使用全局辅助函数处理节点助手可见性(根据用户选择)
                window.ensureNodeHelperVisible(false);
                console.log("Graph mode: Setting node helper visibility based on user preference");
                
                $('#globalParametersJson').closest('.form-group').hide();
                
                // 切换到交互式图形编辑模式
                initGraphMode();
                
                // 确保任务列表选择功能在图形模式下可用
                setTimeout(function() {
                    // 确保工作流任务字段可见
                    $('#workflowTaskFields').show();
                    // 加载可用任务列表（如果节点助手内容可见）
                    if ($('#nodeHelperContent').is(':visible')) {
                        loadAvailableTasksForNodes();
                    }
                }, 100);
                
                // 更改帮助文本
                $('#dagHelpText').text(i18n.translate('tasksPage.modal.workflowTaskFields.graphModeHelp', 
                    '图形模式：直接在图上拖拽节点，创建和编辑连线'));
            }
        });
        
        // 自动排版按钮
        $('#autoLayoutBtn').on('click', function() {
            // 调用增强后的自动布局算法
            if (window.arrangeNodesAutoLayout) {
                window.arrangeNodesAutoLayout();
            } else {
                showFeedback(i18n.translate('tasksPage.feedback.autoLayoutNotReady', '自动布局功能尚未准备好'), true);
            }
        });
        
        // 放大、缩小、重置按钮
        $('#zoomInBtn').on('click', function() {
            zoomDag(1.2); // 放大20%
        });
        
        $('#zoomOutBtn').on('click', function() {
            zoomDag(0.8); // 缩小20%
        });
        
        $('#resetZoomBtn').on('click', function() {
            resetZoom(); // 重置到100%
        });
        
        // 应用图形模式更改
        $('#applyChangesBtn').on('click', function() {
            applyGraphModeChanges();
        });
        
        // 添加新节点按钮（图形模式）
        $('#addNodeBtn').on('click', function() {
            showAddNodeModal();
        });

        // DAG容器点击事件
        $('#dagContainer').on('click', function (event) {
            const target = $(event.target);
            if (window.selectedSourceElement && (
                target.is('#dagContainer') || target.is('svg') || 
                target.is('svg > defs') || target.is('svg > marker') || 
                target.is('svg > path') || target.is('svg > rect') || 
                target.is('svg > text')
            )) {
                // 处理点击事件
            }
        });

        console.log('tasks UI脚本初始化完成');
    });
}
