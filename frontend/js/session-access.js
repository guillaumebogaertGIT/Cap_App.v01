// Preview visibility only. Real accounts must enforce this policy on the server.
(() => {
    window.capSessionAccess = {
        canView(session, role, athleteId) {
            return role === 'coach' || (role === 'athlete' &&
                (session.type !== 'Privé' || Boolean(athleteId && session.athleteIds?.includes(athleteId))));
        }
    };
})();
