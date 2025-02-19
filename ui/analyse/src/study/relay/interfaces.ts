export interface RelayData {
  id: string;
  slug: string;
  description: string;
  markup?: string;
  credit?: string;
  sync: RelaySync;
}

export interface RelaySync {
  ongoing: boolean;
  url: string;
  log: LogEvent[];
  internal?: boolean;
}

export interface RelayIntro {
  exists: boolean;
  active: boolean;
  disable(): void;
}

export interface LogEvent {
  id: string;
  moves: number;
  error?: string;
  at: number;
}
