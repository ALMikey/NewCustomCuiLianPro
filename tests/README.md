# 熔炉消耗回归

执行 `powershell -File tests/run-furnace.ps1`；超级熔炉不在相邻目录时传入 `-SuperFurnace <源码目录>`。

测试编译并调用项目里的真实 FurnaceListener、SuperFurnaceTicker 和 RefinementGuard。
fixtures 仅替代世界、物品元数据和随机淬炼结果，不会打包进插件。

覆盖：一颗/一组宝石、成功/失败不变、结算后连续回放三次、重新支付、未扣费、取消点火、更换输入、Mod 扣费和输出、堆叠保护、2/3/20 倍率隔离与 199 进度边界。

此测试不启动 Uranium，日志中的 getHandle 反射失败是代理世界预期进入的兼容回退路径。真实 TileEntity 点燃、客户端进度及 Forge 模组 NBT 仍须在测试服验收。

部署需同时替换淬炼和超级熔炉 JAR 并重启；建议先取出熔炉中的装备和燃料。原版流程仍在点火时消耗宝石，中途取消不退款；Mod 流程在产出提交时消耗宝石。每次只接受一件装备。
