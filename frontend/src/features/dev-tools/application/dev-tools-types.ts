export interface DevToolFact {
  label: string
  value: string
}

export interface DevToolIssue {
  severity: 'error' | 'warning' | 'info'
  code: string
  message: string
}
