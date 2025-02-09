// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("13.1")
@GraphQLFragment("/graphql/fragment/groupMember.graphql")
data class GitLabGroupMemberDTO(
  val group: GroupDTO
) {
  val projectMemberships: List<GitLabProjectMemberDTO> = group.projects.nodes

  data class GroupDTO(val projects: GitLabUserDetailedDTO.ProjectMemberConnection)
}
