#ifndef CPU_FEATURES_INCLUDE_CPUINFO_AARCH64_H_
#define CPU_FEATURES_INCLUDE_CPUINFO_AARCH64_H_

namespace cpu_features {

struct Aarch64Features {
    bool fp = true;
    bool asimd = true;
    bool sve = false;
    bool sve2 = false;
    bool frint = false;
};

struct Aarch64Info {
    Aarch64Features features;
};

inline Aarch64Info GetAarch64Info() {
    return Aarch64Info{};
}

} // namespace cpu_features

#endif // CPU_FEATURES_INCLUDE_CPUINFO_AARCH64_H_
