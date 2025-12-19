<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# km-quick-mybatis Changelog

## [Unreleased]
### Fix
- fix: https://github.com/wx98/km-quick-mybatis/issues/67

## [1.0.2] - 2025-12-28

### Optimized
- 优化扫描方法调用缓存的扫描过程
- 优化通知的不再建议功能

## [1.0.1] - 2025-12-14

### Added
- 新增右键菜单「刷新细项」选项，支持精准刷新缓存
### Optimized
- 优化缓存刷新策略，精简无用缓存采集，提升运行效率
- 缓存失效通知按场景细化，明确指引刷新范围

## [1.0.0] - 2025-12-07

### Added
- 全新缓存机制，支持项目启动/关闭时自动保存/加载缓存，超大型项目运行更流畅
- 缓存失效提醒功能，缓存过期时主动通知用户刷新
- 全量缓存刷新快捷键，一键触发缓存更新
### Optimized
- 大幅提升缓存扫描、入库速度，支持并行处理文件
- 优化文件变更监听逻辑，新增文件扫描更高效
- 修复索引未就绪时的报错问题，运行更稳定
- 调整包名规范，优化代码结构与编译配置
### Changed
- 替换缓存存储方案，解决大缓存场景下的性能问题

## [0.0.1] - 2025-03-19

### Added
- 初始版本发布，支持 Java 类与 MyBatis XML 文件快速跳转功能
- 基础的代码与 XML 解析能力，实现核心导航功能

[0.0.1]: https://github.com/wx98/km-quick-mybatis/pull/25
[1.0.0]: https://github.com/wx98/km-quick-mybatis/pull/70
[1.0.1]: https://github.com/wx98/km-quick-mybatis/pull/72
[1.0.2]: https://github.com/wx98/km-quick-mybatis/compare/1.0.1...HEAD
[Unreleased]: https://github.com/wx98/km-quick-mybatis/compare/main...feat/1.0.3