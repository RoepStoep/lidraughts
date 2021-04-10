import ExternalTournamentCtrl from './ctrl';

export interface ExternalTournamentSocket {
  send: SocketSend;
  receive(tpe: string, data: any): void;
}

export default function(send: SocketSend, ctrl: ExternalTournamentCtrl) {

  const handlers: any = {
    reload() {
      setTimeout(ctrl.askReload, Math.floor(Math.random() * 500))
    },
    redirect(fullId: string) {
      ctrl.redirectFirst(fullId.slice(0, 8), true);
      return true;
    }
  };

  return {
    send,
    receive(tpe: string, data: any) {
      if (handlers[tpe]) return handlers[tpe](data);
      return false;
    }
  };
};
