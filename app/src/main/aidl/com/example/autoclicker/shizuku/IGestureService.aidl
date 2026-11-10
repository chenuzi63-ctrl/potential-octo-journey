// Shizuku 用户服务对外暴露的注入接口。
// 仅暴露最底层的单事件注入，时序（长按、滑动插值）由调用方协程负责，避免阻塞 Binder 线程。
package com.example.autoclicker.shizuku;

interface IGestureService {
    // 注入单个 MotionEvent；action 取 MotionEvent.ACTION_DOWN / ACTION_MOVE / ACTION_UP。
    boolean inject(int action, float x, float y, long downTime, long eventTime);
}
