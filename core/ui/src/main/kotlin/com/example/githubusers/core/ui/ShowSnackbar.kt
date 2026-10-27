package com.example.githubusers.core.ui

/**
 * アプリ全体で1つの Snackbar を出す。画面を切り替えても（サインイン画面を閉じた後なども）表示を続けるため、
 * `:app` が持つ SnackbarHostState に任せる。
 *
 * @return アクションが押されたら true
 */
typealias ShowSnackbar = suspend (message: String, actionLabel: String?) -> Boolean
