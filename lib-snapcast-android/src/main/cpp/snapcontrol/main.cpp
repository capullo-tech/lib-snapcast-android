#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <iostream>
#include <string>
#include <thread>

static int connectAbstract(const std::string& name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    sockaddr_un a{};
    a.sun_family = AF_UNIX;

    // a.sun_path[0] stays NUL -> abstract namespace
    std::memcpy(a.sun_path + 1, name.data(), name.size());
    socklen_t len = offsetof(sockaddr_un, sun_path) + 1 + name.size();

    return connect(fd, (sockaddr*)&a, len) == 0 ? fd : (close(fd), -1);
}

int main(int argc, char** argv) {
    std::string socketName = "snapcontrol"; // override via --socket-name=...

    int sock = connectAbstract(socketName);
    if (sock < 0) {
        std::cerr << "connect failed\n";
        return 1;
    }

    std::atomic<bool> alive{true};

    // snapserver stdin -> app socket
    std::thread up([&]{
        std::string line;
        while (alive && std::getline(std::cin, line)) {
            line.push_back('\n');
            if (send(sock, line.data(), line.size(), MSG_NOSIGNAL) < 0) {
                break;
            }
        }
        alive = false;
        shutdown(sock, SHUT_RDWR);
    });

    // app socket -> snapserver stdout
    char buf[4096];
    while(alive) {
        ssize_t n = recv(sock, buf, sizeof buf, 0);
        if (n <= 0) {
            break;
        }
        std::cout.write(buf, n);
        std::cout.flush();
    }
    alive = false;
    up.join();
    close(sock);
    return 0;
}