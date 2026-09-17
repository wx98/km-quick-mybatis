# [![km-quick-mybatis][plugin-logo]][gh:km-quick-mybatis] km-quick-mybatis

[![Plugin Homepage][badge:plugin-homepage]][plugin-homepage]
[![Build Status][badge:build]][gh:workflows-build]
[![License][badge:license]][gh:license]
[![GitHub releases][badge:release]][gh:releases]
[![Version][badge:version]][plugin-versions]
[![Downloads][badge:downloads]][plugin-homepage]
[![JavaVersion][badge:JavaVersion]][gh:JavaVersion]
[![IntelliJPlatform][badge:intelliJ-platform]][gh:intelliJ-platform]
[![Last Commit][badge:last-commit]][gh:last-commit]
[![Last Issues][badge:last-issues]][gh:last-issues]

<p align="center"><b>基于IntelliJ IDEs的Java与MyBatisXml互相跳转插件。</b></p>
<p align="center"><img src="./.github/readme/screenshots.gif" alt="screenshots"></p>

[![Getting Started][badge:get-started-en]][get-started-en]
[![开始使用][badge:get-started-zh]][get-started-zh]
---

* Km-Quick-Mybatis 是一款 IntelliJ IDEA 插件，用于实现 Java 类与 MyBatis Mapper 文件之间的快速导航。
* 它可以帮助开发者提高开发效率，减少手动查找文件的时间。
* 该插件与 IntelliJ IDEA 无缝集成，使 MyBatis 开发更加高效和愉悦。

## 安装方法
### 使用 JetBrains 插件市场：
  <a href="https://plugins.jetbrains.com/plugin/29383-km-quick-mybatis" target="_blank"><img src="./.github/readme/install-button-cn.png" height="52" alt="从JetBrains插件市场获取" title="从JetBrains插件市场获取"> </a>
  
- 访问 [JetBrains 插件市场](https://plugins.jetbrains.com/plugin/29383-km-quick-mybatis)，如果您的IDE正在运行，点击 <kbd>Install to ...</kbd> 按钮即可进行安装。

### 使用 IDE 内置插件系统：
- <kbd>设置/偏好设置</kbd> → <kbd>插件</kbd> → <kbd>Marketplace</kbd> → <kbd>搜索"km-quick-mybatis"</kbd> → <kbd>安装</kbd>  
  <img src="./.github/readme/install-plugins-in-ide-cn.png" height="500">

### 从 JetBrains 插件市场下载安装：
- 您也可以从 JetBrains 插件市场下载 [最新版本](https://plugins.jetbrains.com/plugin/29383-km-quick-mybatis/versions)
- 并手动安装：<kbd>设置/偏好设置</kbd> → <kbd>插件</kbd> → <kbd>⚙️</kbd> → <kbd>从磁盘安装插件...</kbd>

### 从 Git Releases 下载安装：
- 下载 [最新发布版本](https://github.com/wx98/km-quick-mybatis/releases/latest) 并手动安装：
- <kbd>设置/偏好设置</kbd> → <kbd>插件</kbd> → <kbd>⚙️</kbd> → <kbd>从磁盘安装插件...</kbd>


---
插件基于 [IntelliJ Platform Plugin Template][template] 构建。

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

[plugin-logo]: src/main/resources/images/icon-32.svg
[plugin-homepage]: https://plugins.jetbrains.com/plugin/29383-km-quick-mybatis
[plugin-versions]: https://plugins.jetbrains.com/plugin/29383-km-quick-mybatis

[badge:plugin-homepage]: https://img.shields.io/badge/plugin%20homepage-km--quick--mybatis-4caf50.svg?style=flat-square
[badge:build]: https://img.shields.io/github/actions/workflow/status/wx98/km-quick-mybatis/build.yml?branch=main&label=build&style=flat-square
[badge:license]: https://img.shields.io/github/license/wx98/km-quick-mybatis.svg?style=flat-square
[badge:version]: https://img.shields.io/jetbrains/plugin/v/29383-km-quick-mybatis.svg
[badge:downloads]: https://img.shields.io/jetbrains/plugin/d/29383-km-quick-mybatis.svg?style=flat-square&colorB=5C6BC0
[badge:release]: https://img.shields.io/github/release/wx98/km-quick-mybatis.svg?sort=semver&style=flat-square&colorB=0097A7
[badge:JavaVersion]: https://img.shields.io/badge/Java-17-blue.svg
[badge:intelliJ-platform]: https://img.shields.io/badge/IntelliJ%20Platform-2023.1%2B-blue.svg
[badge:last-commit]: https://img.shields.io/github/last-commit/wx98/km-quick-mybatis.svg
[badge:last-issues]: https://img.shields.io/github/issues/wx98/km-quick-mybatis.svg
[badge:get-started-en]: https://img.shields.io/badge/Get%20Started-English-4CAF50?style=flat-square
[badge:get-started-zh]: https://img.shields.io/badge/%E5%BC%80%E5%A7%8B%E4%BD%BF%E7%94%A8-%E4%B8%AD%E6%96%87-2196F3?style=flat-square


[gh:km-quick-mybatis]: https://github.com/wx98/km-quick-mybatis
[gh:workflows-build]: https://github.com/wx98/km-quick-mybatis/actions/workflows/build.yml
[gh:releases]: https://github.com/wx98/km-quick-mybatis/releases
[gh:license]: https://github.com/wx98/km-quick-mybatis/blob/main/LICENSE
[gh:JavaVersion]: https://jdk.java.net/archive/
[gh:intelliJ-platform]: https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
[gh:last-commit]: https://github.com/wx98/km-quick-mybatis/commits/main
[gh:last-issues]: https://github.com/wx98/km-quick-mybatis/issues

[get-started-en]: https://github.com/wx98/km-quick-mybatis/blob/main/README_en.md
[get-started-zh]: https://github.com/wx98/km-quick-mybatis/blob/main/README.md

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
