// SPDX-License-Identifier: GPL-3.0-only
// Development receiver: validates real shared textures, never distributed as a runtime dependency.
#include "SpoutDX.h"
#include <fstream>
#include <vector>
#include <chrono>
#include <iostream>

int main(int argc, char** argv) {
    spoutDX receiver;
    if (!receiver.OpenDirectX11()) return 2;
    if (argc == 1) {
        for (const auto& name : receiver.GetSenderList()) std::cout << name << "\n";
        return 0;
    }
    receiver.SetReceiverName(argv[1]);
    const auto start = std::chrono::steady_clock::now();
    int stagingFrames = 0;
    while (std::chrono::steady_clock::now() - start < std::chrono::seconds(15)) {
        receiver.ReceiveTexture();
        receiver.IsUpdated(); // acknowledge the texture dimensions before accessing it
        if (receiver.IsConnected() && receiver.GetSenderTexture()) {
            unsigned w = receiver.GetSenderWidth(), h = receiver.GetSenderHeight();
            std::vector<unsigned char> pixels(static_cast<size_t>(w) * h * 4);
            if (receiver.ReadTexurePixels(receiver.GetSenderTexture(), pixels.data())) {
                // SDK readback is double-buffered: the first staging texture has no previous frame.
                if (++stagingFrames < 3) { Sleep(16); continue; }
                if (argc > 2) {
                    std::ofstream output(argv[2], std::ios::binary);
                    output.write(reinterpret_cast<char*>(&w), 4); output.write(reinterpret_cast<char*>(&h), 4);
                    output.write(reinterpret_cast<char*>(pixels.data()), pixels.size());
                }
                std::cout << "RECEIVED " << receiver.GetSenderName() << " " << w << "x" << h
                    << " format=" << receiver.GetSenderFormat() << " frame=" << receiver.GetSenderFrame() << "\n";
                receiver.ReleaseReceiver(); receiver.CloseDirectX11(); return 0;
            }
        }
        Sleep(16);
    }
    std::cerr << "No shared texture received\n"; return 3;
}
