/**
 * lazy-loader.js
 * 为表单字段实现延迟加载功能，减少页面初始加载时间
 */

// 避免重复初始化
if (typeof window.lazyLoaderInitialized === 'undefined') {
    console.log('初始化延迟加载脚本...');
    window.lazyLoaderInitialized = true;

    /**
     * 延迟加载状态对象
     */
    const LazyLoadState = {
        calendarsLoaded: false,
        usersLoaded: false,
        tasksLoaded: false,
        lastLoadTime: { // 添加加载时间戳，用于缓存过期判断
            calendars: 0,
            users: 0,
            tasks: 0
        },
        cacheTimeout: 5 * 60 * 1000 // 缓存5分钟过期
    };

    /**
     * 使用延迟加载替换原始的日历加载函数
     */
    if (typeof window.originalLoadCalendarsForTaskForm === 'undefined' && typeof window.loadCalendarsForTaskForm === 'function') {
        window.originalLoadCalendarsForTaskForm = window.loadCalendarsForTaskForm;
        
        window.loadCalendarsForTaskForm = function(forceReload) {
            const now = Date.now();
            const cacheExpired = (now - LazyLoadState.lastLoadTime.calendars) > LazyLoadState.cacheTimeout;
            
            if (LazyLoadState.calendarsLoaded && !forceReload && !cacheExpired) {
                if (window.debugMode) {
                    console.log('日历数据已缓存，不需要重新加载');
                }
                return;
            }
            
            console.log('延迟加载日历数据...' + (forceReload ? '(强制刷新)' : '') + (cacheExpired ? '(缓存过期)' : ''));
            window.originalLoadCalendarsForTaskForm();
            LazyLoadState.calendarsLoaded = true;
            LazyLoadState.lastLoadTime.calendars = now;
        };
    }

    /**
     * 使用延迟加载替换原始的用户加载函数
     */
    if (typeof window.originalLoadUsersForNotifications === 'undefined' && typeof window.loadUsersForNotifications === 'function') {
        window.originalLoadUsersForNotifications = window.loadUsersForNotifications;
        
        window.loadUsersForNotifications = function(forceReload) {
            const now = Date.now();
            const cacheExpired = (now - LazyLoadState.lastLoadTime.users) > LazyLoadState.cacheTimeout;
            
            if (LazyLoadState.usersLoaded && !forceReload && !cacheExpired) {
                if (window.debugMode) {
                    console.log('用户数据已缓存，不需要重新加载');
                }
                return;
            }
            
            console.log('延迟加载用户数据...' + (forceReload ? '(强制刷新)' : '') + (cacheExpired ? '(缓存过期)' : ''));
            window.originalLoadUsersForNotifications();
            LazyLoadState.usersLoaded = true;
            LazyLoadState.lastLoadTime.users = now;
        };
    }

    /**
     * 延迟加载节点任务数据
     */
    if (typeof window.originalLoadAvailableTasksForNodes === 'undefined' && typeof window.loadAvailableTasksForNodes === 'function') {
        window.originalLoadAvailableTasksForNodes = window.loadAvailableTasksForNodes;
        
        window.loadAvailableTasksForNodes = function(forceReload) {
            const now = Date.now();
            const cacheExpired = (now - LazyLoadState.lastLoadTime.tasks) > LazyLoadState.cacheTimeout;
            
            if (LazyLoadState.tasksLoaded && !forceReload && !cacheExpired) {
                if (window.debugMode) {
                    console.log('节点任务数据已缓存，不需要重新加载');
                }
                return;
            }
            
            console.log('延迟加载节点任务数据...' + (forceReload ? '(强制刷新)' : '') + (cacheExpired ? '(缓存过期)' : ''));
            window.originalLoadAvailableTasksForNodes();
            LazyLoadState.tasksLoaded = true;
            LazyLoadState.lastLoadTime.tasks = now;
        };
    }

    /**
     * 重置延迟加载状态，强制在下次打开表单时重新加载所有数据
     * 用于处理可能的数据变化情况
     */
    window.resetLazyLoadState = function() {
        LazyLoadState.calendarsLoaded = false;
        LazyLoadState.usersLoaded = false;
        LazyLoadState.tasksLoaded = false;
        
        // 重置缓存时间戳
        LazyLoadState.lastLoadTime.calendars = 0;
        LazyLoadState.lastLoadTime.users = 0;
        LazyLoadState.lastLoadTime.tasks = 0;
        
        console.log('已重置延迟加载状态，下次将重新加载所有数据');
    };

    /**
     * 任务保存成功后重置延迟加载状态
     */
    $(document).on('taskSaved', function() {
        window.resetLazyLoadState();
    });

    /**
     * 每10分钟重置一次延迟加载状态，确保长时间打开页面时数据不会过时
     */
    setInterval(function() {
        window.resetLazyLoadState();
    }, 10 * 60 * 1000); // 10分钟
    
    console.log('延迟加载初始化完成');
}
