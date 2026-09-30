"""FocusQuest's camera companion program.

It runs next to the backend, is told by it when a camera-verified session is running, and reports
what it sees as observations: stretches of one off-task signal. Frames are processed in memory only,
never saved, uploaded or sent to the backend (requirements specification, sections 19 and 21).
"""
