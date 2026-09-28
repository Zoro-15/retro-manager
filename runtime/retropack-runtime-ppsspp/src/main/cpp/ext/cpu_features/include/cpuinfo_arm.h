#ifndef CPU_FEATURES_INCLUDE_CPUINFO_ARM_H_
#define CPU_FEATURES_INCLUDE_CPUINFO_ARM_H_

namespace cpu_features {

struct ArmFeatures {
    bool swp = true;
    bool half = true;
    bool thumb = true;
    bool fastmult = true;
    bool edsp = true;
    bool thumbee = false;
    bool neon = true;
    bool tls = true;
    bool vfp = true;
    bool vfpv3 = true;
    bool vfpv4 = true;
    bool idiva = true;
    bool idivt = true;
};

struct ArmInfo {
    ArmFeatures features;
};

inline ArmInfo GetArmInfo() {
    return ArmInfo{};
}

} // namespace cpu_features

#endif // CPU_FEATURES_INCLUDE_CPUINFO_ARM_H_
