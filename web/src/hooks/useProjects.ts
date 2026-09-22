import { useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { projectsApi } from '@/api/endpoints/projects';
import { useAppStore } from '@/stores/appStore';
import { useCurrentUser } from '@/hooks/useAuth';

export function useProjects() {
  const { data: user } = useCurrentUser();
  return useQuery({
    queryKey: ['projects'],
    queryFn: projectsApi.list,
    enabled: !!user,
  });
}

/** The active project plus a setter, auto-selecting the first project once the list loads. */
export function useCurrentProject() {
  const { data: projects } = useProjects();
  const currentProjectId = useAppStore((s) => s.currentProjectId);
  const setCurrentProjectId = useAppStore((s) => s.setCurrentProjectId);

  useEffect(() => {
    if (!projects || projects.length === 0) return;
    const stillValid = projects.some((p) => p.id === currentProjectId);
    if (!stillValid) setCurrentProjectId(projects[0].id);
  }, [projects, currentProjectId, setCurrentProjectId]);

  const currentProject = useMemo(
    () => projects?.find((p) => p.id === currentProjectId) ?? null,
    [projects, currentProjectId],
  );

  return { currentProject, projects: projects ?? [], setCurrentProjectId };
}

export function useCanConfirm(): boolean {
  const { currentProject } = useCurrentProject();
  return currentProject?.canConfirm ?? false;
}
