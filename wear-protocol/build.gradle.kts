// フォン(:app)とウォッチ(:wear)の間だけで共有する、Android非依存のプロトコル定数モジュール。
// RelayLabCommon(PhotoRelayとも共有する別リポジトリ)とは役割が異なるため、あえて分けている。
// このリポジトリのサブプロジェクトとしてのみ使う想定(RelayLabCommonと違い、単独ビルドは想定しない)ため、
// バージョンはルートのbuild.gradle.ktsで解決する(ここでは指定しない)。
plugins {
    id("org.jetbrains.kotlin.jvm")
}
