// SPDX-License-Identifier: GPL-3.0-only
#include "SpoutDX.h"
#include <memory>
#include <stdexcept>
#include <string>

namespace {
thread_local std::string last_error;
struct Sender {
    spoutDX dx;
    ~Sender() { dx.ReleaseSender(); dx.CloseDirectX11(); }
};
void failure(const char* message) { last_error = message; }
}
#define API extern "C" __declspec(dllexport)

// All calls for a handle must come from its owner thread. BGRA8 premultiplied, top-down.
API void* spw_spout_create(const char* name, int adapter) noexcept {
    try {
        last_error.clear();
        auto sender = std::make_unique<Sender>();
        if (adapter >= 0 && !sender->dx.SetAdapter(adapter)) throw std::runtime_error("Invalid DXGI adapter");
        if (!sender->dx.OpenDirectX11()) throw std::runtime_error("D3D11 device initialization failed");
        if (!sender->dx.SetSenderName(name)) throw std::runtime_error("Sender name registration failed");
        sender->dx.SetSenderFormat(DXGI_FORMAT_B8G8R8A8_UNORM);
        return sender.release();
    } catch (const std::exception& error) { failure(error.what()); }
      catch (...) { failure("Unknown native initialization error"); }
    return nullptr;
}

API int spw_spout_send(void* handle, const unsigned char* pixels, int width, int height) noexcept {
    try {
        if (!handle || !pixels || width < 1 || width > 3840 || height < 1 || height > 2160)
            throw std::runtime_error("Invalid frame dimensions or pointer");
        auto& sender = *static_cast<Sender*>(handle);
        if (FAILED(sender.dx.GetDX11Device()->GetDeviceRemovedReason()))
            throw std::runtime_error("D3D11 device lost");
        if (!sender.dx.SendImage(pixels, width, height, width * 4))
            throw std::runtime_error("Shared texture upload failed");
        return 1;
    } catch (const std::exception& error) { failure(error.what()); }
      catch (...) { failure("Unknown native send error"); }
    return 0;
}
API void spw_spout_destroy(void* handle) noexcept { delete static_cast<Sender*>(handle); }
API const char* spw_spout_error() noexcept { return last_error.c_str(); }
API const char* spw_spout_name(void* handle) noexcept {
    return handle ? static_cast<Sender*>(handle)->dx.GetName() : "";
}
