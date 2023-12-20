import sbt.{Def, Keys, Setting, Task, TaskKey}

object Utilities {
  implicit class TaskKeyOps(tk: TaskKey[Unit]) {
    def deactivate(): Setting[Task[Unit]] = {
      tk := Def.task {
        import Keys.{streams, thisProject}
        val localStreams = (streams in tk.scopedKey.scope).value
        val currentProjectName = (thisProject in tk.scopedKey.scope).value.id
        localStreams.log.info(s"Task ${tk.key.label} is deactivated in ${currentProjectName}")
//        tk.value
      }.value
    }
  }
}

object GhPackages {
  import sbt.librarymanagement.MavenRepository
  import sbt.librarymanagement.DependencyBuilders
  import sbt.Credentials

  val owner = "Saldubatech"
  val sourceRepo = "packages"
  // The next two are **FIXED** by GH.
  val name = "GitHub Package Registry"
  val host = "maven.pkg.github.com"
  val packagesRepo = s"https://$host/$owner/$sourceRepo"

  val repo: MavenRepository = (new DependencyBuilders {}).toRepositoryName(name).at(packagesRepo)

  def credentials(user: String, secretVariableName: String): Option[Credentials] = 
    sys.env.get(secretVariableName).map{ ghTk => Credentials(name, host, user, ghTk) }

}
