#pragma once
#include <string>
#include <cstdint>

struct Identifier {
    std::string name;
    Identifier() = default;
    Identifier(const std::string& n) : name(n) {}
};

struct LabelDefinition {
    Identifier name;
    uint64_t value{0};
};
