export const PLAYER_INSTALL_COMMAND =
  process.env.NEXT_PUBLIC_PLAYER_INSTALL_COMMAND ??
  'git clone https://github.com/marcoslafoz/cefa-tv-player.git cefa-tv-player && cd cefa-tv-player && sudo ./install.sh'
