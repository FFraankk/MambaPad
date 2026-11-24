package main

import "strings"

func connectionStatus(message string) (string, string) {
	switch {
	case strings.Contains(message, "Multiple phones"):
		return "请选择要连接的手机", "检测到多台 USB 手机，请在设备列表中选择。"
	case strings.Contains(message, "Unlock phone"):
		return "等待手机授权", "请解锁手机，并确认 USB 调试授权弹窗。"
	case strings.Contains(message, "App not installed"):
		return "手机应用未就绪", "请安装 Android 版 MambaPad，并在手机上打开 USB 有线模式。"
	case strings.Contains(message, "USB ready"):
		return "等待手机应用连接", "USB 通道已就绪，请在手机上打开 MambaPad 的 USB 有线模式。"
	case strings.Contains(message, "Waiting for phone"):
		return "等待 USB 连接", "检查数据线、手机 USB 调试和厂商驱动。"
	case strings.Contains(message, "Cannot inspect"):
		return "无法检查 USB 通道", "请确认手机在线，然后重新连接。"
	case strings.Contains(message, "Cannot create"):
		return "USB 通道被占用", "其他工具可能占用了手机端口，请关闭冲突连接后重试。"
	default:
		return "连接失败", message
	}
}
