export const PLAYER_INSTALL_COMMAND =
  process.env.NEXT_PUBLIC_PLAYER_INSTALL_COMMAND ??
  'git clone ssh://git@git.cefa.es:2222/it/cefa-tv-player.git cefa-tv-player && cd cefa-tv-player && sudo ./install.sh'
