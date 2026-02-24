# ファイル情報

```SH
* すでに GitHub リポジトリを用意できていますか？ [y / n]
y

* 作成した GitHub リポジトリの URL を入力してください。
* ex) https://github.com/cs-u-gakugei-ac-jp/java_app_sample
https://github.com/toichan/programming-process-evaluator
* リポジトリの URL は、 https://github.com/toichan/programming-process-evaluator でよろしいですか？ [y / n]
y

* ghq get https://github.com/toichan/programming-process-evaluator
    exists /Users/t.toida/ghq/github.com/toichan/programming-process-evaluator

* java_app_sample ディレクトリの内容をコピーしています。
* done!

* 不必要なファイルを削除しています。
rm: /Users/t.toida/ghq/github.com/toichan/programming-process-evaluator/.env: No such file or directory
* done!

* .env ファイルを作成します。
* ローカルから DB コンテナと疎通を行うポート番号を入力してください。（ポート番号は、既に使用しているものとかぶらないようにしましょう。）
ex) 3333
3336
* DB コンテナと疎通を行うポート番号は、 3336 でよろしいですか？ [y / n]
y

* ローカルから Tomcat コンテナと疎通を行うポート番号を入力してください。（ポート番号は、既に使用しているものとかぶらないようにしましょう。）
ex) 8888
8891
* Tomcat コンテナと疎通を行うポート番号は、 8891 でよろしいですか？ [y / n]
y

* デフォルトで生成する DB の名前を入力してください。
ppe_db
* デフォルトで生成する DB の名前は、 ppe_db でよろしいですか？ [y / n]
y
* done!

README.md の初期化を行います。
* done!

* GitHub の諸設定を行います。
* $ git init
hint: Using 'master' as the name for the initial branch. This default branch name
hint: is subject to change. To configure the initial branch name to use in all
hint: of your new repositories, which will suppress this warning, call:
hint:
hint: 	git config --global init.defaultBranch <name>
hint:
hint: Names commonly chosen instead of 'master' are 'main', 'trunk' and
hint: 'development'. The just-created branch can be renamed via this command:
hint:
hint: 	git branch -m <name>
Initialized empty Git repository in /Users/t.toida/ghq/github.com/toichan/programming-process-evaluator/.git/
* $ git remote add origin https://github.com/toichan/programming-process-evaluator.git
* $ git add -A
* $ git commit -m "first commit (reference: https://github.com/cs-u-gakugei-ac-jp/java_app_sample)
[master (root-commit) ab2e077] first commit (reference: https://github.com/cs-u-gakugei-ac-jp/java_app_sample)
 14 files changed, 184 insertions(+)
 create mode 100644 .env.sample
 create mode 100644 .github/ISSUE_TEMPLATE.md
 create mode 100644 .github/PULL_REQUEST_TEMPLATE.md
 create mode 100644 .gitignore
 create mode 100644 README.md
 create mode 100644 build.gradle
 create mode 100644 containers/app/Dockerfile
 create mode 100644 containers/db/Dockerfile
 create mode 100644 containers/gradle/Dockerfile
 create mode 100644 docker-compose.yml
 create mode 100644 settings.gradle
 create mode 100644 src/main/java/controller/HelloWorldServlet.java
 create mode 100644 src/main/java/lib/mysql/Client.java
 create mode 100644 src/main/webapp/WEB-INF/hello/index.jsp
* $ git push origin main
error: src refspec main does not match any
error: failed to push some refs to 'https://github.com/toichan/programming-process-evaluator.git'
* done!

セットアップが完了しました。以下の手順に則ると、開発を始められます。

1. $ ghq look programming-process-evaluator ( 当該ディレクトリに移動する。 )
2. $ docker-compose build ( イメージのビルドを行う。 )
3. $ docker-compose up ( コンテナの起動を行う。 )
4. http://localhost:8891 にブラウザ等でアクセスし、アプリケーションが動作することを確認する。
5. 以下の情報で、DB にアクセスし、疎通確認を行う。 ( TablePlus 等を用いるとよいのかな。 )
  - User: root
  - Password: root
  - Port: 3336
  - DB名: ppe_db
  ```