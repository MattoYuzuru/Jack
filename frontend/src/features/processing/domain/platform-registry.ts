import {
  getPlatformCapabilityMatrix,
  type ProcessingPlatformCapabilityMatrix,
  type ProcessingPlatformModuleCapability,
} from '../application/processing-client'

export async function getProcessingPlatformMatrix(): Promise<ProcessingPlatformCapabilityMatrix> {
  return getPlatformCapabilityMatrix()
}

export async function getProcessingPlatformModules(): Promise<ProcessingPlatformModuleCapability[]> {
  const matrix = await getProcessingPlatformMatrix()
  return matrix.modules
}

export async function resolveProcessingPlatformModule(
  moduleId: string,
): Promise<ProcessingPlatformModuleCapability | null> {
  const modules = await getProcessingPlatformModules()
  return modules.find((module) => module.id === moduleId) ?? null
}
