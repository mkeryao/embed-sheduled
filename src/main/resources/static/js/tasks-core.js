/**
 * tasks-core.js
 * 任务管理核心功能，包含任务加载、过滤、CRUD操作等
 */

// 避免重复初始化
if (typeof window.tasksCoreInitialized === 'undefined') {
    console.log('初始化tasks core脚本...');
    window.tasksCoreInitialized = true;

    // 加载任务列表的函数
    window.loadTasks = function() {
        const filters = {
            taskName: $('#filterTaskName').val().trim(),
            taskGroup: $('#filterTaskGroup').val(),
            taskType: $('#filterTaskType').val() ? parseInt($('#filterTaskType').val()) : null,
            isActive: $('#filterIsActive').val() ? ($('#filterIsActive').val() === 'true') : null
        };
        Object.keys(filters).forEach(key => (filters[key] === null || filters[key] === '' || (typeof filters[key] === 'string' && filters[key].trim() === '')) && delete filters[key]);

        const queryString = $.param(filters);
        const apiUrl = queryString ? `/tasks?${queryString}` : '/tasks';

        makeApiCall('GET', apiUrl, null,
            function (tasks) {
                const tableBody = $('#tasks-table-body');
                tableBody.empty();

                if (!window.initialGroupPopulationDone && Object.keys(filters).length === 0) { // 只在首次全量加载时填充分组
                    // 如果还没有填充过分组并且没有应用过滤器，获取所有任务以填充分组
                    makeApiCall('GET', '/tasks', null, function (allTasks) {
                        populateGroupFilter(allTasks);
                    }, function (jqXHR) {
                        console.error("Error fetching all tasks for group population:", jqXHR);
                    });
                }

                tasks.forEach(function (task) {
                    const status = task.active ? `<span class="badge badge-success">${i18n.translate('tasksPage.table.statusActive', 'Active')}</span>` : `<span class="badge badge-secondary">${i18n.translate('tasksPage.table.statusInactive', 'Inactive')}</span>`;
                    const historyBtnText = i18n.translate('tasksPage.table.historyBtnShort', 'History');
                    const editBtnText = i18n.translate('common.edit', 'Edit');
                    const deleteBtnText = i18n.translate('common.delete', 'Delete');
                    const triggerBtnText = i18n.translate('tasksPage.table.triggerBtnShort', 'Trigger');
                    const disableBtnText = i18n.translate('tasksPage.table.disableBtnShort', 'Disable');
                    const enableBtnText = i18n.translate('tasksPage.table.enableBtnShort', 'Enable');

                    const row = `<tr>
                        <td>${task.taskId}</td>
                        <td>${task.taskGroup || ''}</td>
                        <td>${task.taskName || ''}</td>
                        <td>${task.cronExpression || ''}</td>
                        <td>${getTaskTypeString(task.taskType)}</td>
                        <td>${status}</td>
                        <td>${task.executionMode || 'BROADCAST'}</td>
                        <td class="action-buttons">
                            <a href="logs.html?taskId=${task.taskId}" class="btn btn-sm btn-outline-info mr-1" title="${i18n.translate('tasksPage.table.historyBtn', 'View Execution History')}">${historyBtnText}</a>
                            <button class="btn btn-sm btn-info edit-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.editBtn', 'Edit Task')}">${editBtnText}</button>
                            <button class="btn btn-sm btn-danger delete-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.deleteBtn', 'Delete Task')}">${deleteBtnText}</button>
                            <button class="btn btn-sm btn-secondary trigger-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.triggerBtn', 'Trigger Task Manually')}">${triggerBtnText}</button>
                            ${task.active
                            ? `<button class="btn btn-sm btn-warning disable-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.disableBtn', 'Disable Task')}">${disableBtnText}</button>`
                            : `<button class="btn btn-sm btn-success enable-btn" data-id="${task.taskId}" title="${i18n.translate('tasksPage.table.enableBtn', 'Enable Task')}">${enableBtnText}</button>`}
                        </td>
                    </tr>`;
                    tableBody.append(row);
                });
            },
            function (jqXHR) {
                showFeedback(i18n.translate('tasksPage.feedback.errorLoadingTasks', 'Error loading tasks: {{error}}').replace('{{error}}', (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
            }
        );
    };

    // 填充任务分组过滤器的函数
    window.populateGroupFilter = function(allTasks) {
        const groupSelect = $('#filterTaskGroup');
        groupSelect.find('option:gt(0)').remove(); // 清除除"All Groups"以外的现有分组
        const uniqueGroups = new Set();
        allTasks.forEach(task => {
            if (task.taskGroup && task.taskGroup.trim() !== '') {
                uniqueGroups.add(task.taskGroup.trim());
            }
        });
        // 按字母顺序对分组进行排序，以保持一致的顺序
        Array.from(uniqueGroups).sort().forEach(group => {
            groupSelect.append(`<option value="${group}">${group}</option>`);
        });
        window.initialGroupPopulationDone = true;
    };

    // 获取任务类型字符串
    window.getTaskTypeString = function(typeInt) {
        switch (typeInt) {
            case 0:
                return i18n.translate('tasksPage.modal.taskTypes.bean', 'Bean Task');
            case 2:
                return i18n.translate('tasksPage.modal.taskTypes.http', 'HTTP Task');
            case 4:
                return i18n.translate('tasksPage.modal.taskTypes.shell', 'Shell Script Task');
            case 10:
                return i18n.translate('tasksPage.modal.taskTypes.workflow', 'Workflow Task');
            default:
                return i18n.translate('tasksPage.modal.taskTypes.unknown', 'Unknown ({{typeInt}})').replace('{{typeInt}}', typeInt);
        }
    };

    // 加载用户选择框
    window.loadUsersForSelects = function() {
        console.log("loadUsersForSelects: Function called.");
        const successSelect = $('#notifySuccessUserIds');
        const failSelect = $('#notifyFailedUserIds');

        console.log("loadUsersForSelects: Making API call to /users");
        makeApiCall('GET', '/users', null,
            function (users) {
                console.log("loadUsersForSelects: API call successful. Received users:", users);
                successSelect.empty(); // 清除之前的选项
                failSelect.empty();   // 清除之前的选项

                if (users && Array.isArray(users)) {
                    console.log("loadUsersForSelects: Number of users received:", users.length);
                    let optionsAdded = 0;
                    users.forEach(function (user) {
                        const option = `<option value='${user.userId}'>${user.username}</option>`;
                        successSelect.append(option);
                        failSelect.append(option);
                        optionsAdded++;
                    });
                    console.log("loadUsersForSelects: Options added to dropdowns:", optionsAdded);
                    if (optionsAdded === 0 && users.length > 0) {
                        console.warn("loadUsersForSelects: Users received, but no options added (unexpected).");
                    } else if (users.length === 0) {
                        console.warn("loadUsersForSelects: API returned an empty list of users.");
                    }
                } else {
                    console.warn("loadUsersForSelects: API response was not an array or was null/undefined for users.");
                }
            },
            function (jqXHR) {
                console.error("loadUsersForSelects: API call to /users failed.", jqXHR);
                showFeedback(i18n.translate('tasksPage.feedback.errorLoadingUsers', 'Error loading users: {{error}}').replace('{{error}}', (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
            }
        );
    };

    // 加载日历选择框
    window.loadCalendarsForSelect = function() {
        makeApiCall('GET', '/calendars', null,
            function (calendars) {
                const calendarSelect = $('#taskCalendarGroup');
                calendarSelect.find('option:gt(0)').remove(); // 保留第一个"-- Select Calendar --"选项
                calendars.forEach(function (calendar) {
                    const option = `<option value='${calendar.calendarName}'>${calendar.calendarName}</option>`;
                    calendarSelect.append(option);
                });
            },
            function (jqXHR) {
                showFeedback(i18n.translate('tasksPage.feedback.errorLoadingCalendars', 'Error loading calendars: {{error}}').replace('{{error}}', (jqXHR.responseJSON ? jqXHR.responseJSON.message : jqXHR.statusText)), true);
            }
        );
    };

    // 加载节点任务选择框
    window.loadAvailableTasksForNodes = function() {
        console.log("loadAvailableTasksForNodes: Function called.");
        const select = $('#availableTasksForNodes');
        const placeholderText = i18n.translate('tasksPage.modal.workflowTaskFields.selectTaskPlaceholder', '-- Select a Task --');
        select.empty().append(`<option value="">${placeholderText}</option>`);

        console.log("loadAvailableTasksForNodes: Making API call to /tasks");
        makeApiCall('GET', '/tasks', null,
            function (tasks) {
                console.log("loadAvailableTasksForNodes: API call successful. Received tasks:", tasks);
                if (tasks && Array.isArray(tasks)) {
                    console.log("loadAvailableTasksForNodes: Number of tasks received:", tasks.length);
                    let optionsAdded = 0;
                    tasks.forEach(function (task) {
                        // 确保不选择工作流任务（类型为10）作为节点
                        if ((task.taskType != 10) && task.active) {
                            const optionText = `${task.taskName} (ID: ${task.taskId}, Group: ${task.taskGroup || 'N/A'}, Type: ${task.taskType})`;
                            select.append(`<option value='${task.taskId}'>${optionText}</option>`);
                            optionsAdded++;
                        } else if (task.taskType == 10) {
                            console.log("Excluded workflow task from node selection:", task.taskName, "ID:", task.taskId, "Type:", task.taskType);
                        } else if (!task.active) {
                            console.log("Excluded inactive task from node selection:", task.taskName, "ID:", task.taskId, "Type:", task.taskType);
                        }
                    });
                    console.log("loadAvailableTasksForNodes: Options added to dropdown:", optionsAdded);
                    if (optionsAdded === 0 && tasks.length > 0) {
                        console.warn("loadAvailableTasksForNodes: No tasks met the criteria (type != 10 and isActive) to be added as nodes.");
                    } else if (tasks.length === 0) {
                        console.warn("loadAvailableTasksForNodes: API returned an empty list of tasks.");
                    }
                } else {
                    console.warn("loadAvailableTasksForNodes: API response was not an array or was null/undefined.");
                }
            },
            function (jqXHR) {
                console.error("loadAvailableTasksForNodes: API call failed.", jqXHR);
            }
        );
    };

    // 加载节点编辑模态框的任务选择列表
    window.loadAvailableTasksForEditNode = function() {
        console.log("loadAvailableTasksForEditNode: 正在加载编辑节点的任务选择列表");
        const select = $('#editTaskConfigId');
        const placeholderText = i18n.translate('tasksPage.nodeEditModal.selectTaskPlaceholder', '-- 选择任务 --');
        select.empty().append(`<option value="">${placeholderText}</option>`);

        makeApiCall('GET', '/tasks', null,
            function (tasks) {
                console.log("loadAvailableTasksForEditNode: API调用成功，获取任务:", tasks);
                if (tasks && Array.isArray(tasks)) {
                    console.log("loadAvailableTasksForEditNode: 获取任务数量:", tasks.length);
                    let optionsAdded = 0;
                    tasks.forEach(function (task) {
                        // 确保不选择工作流任务（类型为10）作为节点
                        if ((task.taskType != 10) && task.active) {
                            const optionText = `${task.taskName} (ID: ${task.taskId}, Group: ${task.taskGroup || 'N/A'}, Type: ${task.taskType})`;
                            select.append(`<option value='${task.taskId}'>${optionText}</option>`);
                            optionsAdded++;
                        }
                    });
                    console.log("loadAvailableTasksForEditNode: 添加到下拉菜单的选项数:", optionsAdded);
                } else {
                    console.warn("loadAvailableTasksForEditNode: API返回非数组或为空");
                }
            },
            function (jqXHR) {
                console.error("loadAvailableTasksForEditNode: API调用失败", jqXHR);
            }
        );
    };

    // JSON处理辅助函数
    window.parseJsonOrNull = function(jsonString) {
        if (!jsonString || jsonString.trim() === '') {
            return null;
        }
        try {
            return JSON.parse(jsonString);
        } catch (e) {
            showFeedback(i18n.translate('tasksPage.feedback.invalidJson', "Invalid JSON: {{error}}").replace("{{error}}", e.message), true);
            throw e;
        }
    };

    console.log('tasks core脚本初始化完成');
}
